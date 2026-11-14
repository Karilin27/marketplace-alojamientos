# Prueba en vivo de disponibilidad (HU-008), reservas recibidas/listado (HU-014/015) y notificaciones (HU-026).
# Requisito: backend corriendo con las variables de entorno y los datos de prueba (anfitrion a001, alojamientos a0001..a0003).
# Uso:  powershell -ExecutionPolicy Bypass -File .\prueba-verificacion.ps1 -Secret "<el mismo JWT_SECRET del backend>"
param(
    [Parameter(Mandatory = $true)][string]$Secret,
    [string]$BaseUrl = "http://localhost:8080"
)

$Anfitrion = "00000000-0000-0000-0000-00000000a001"
$Huesped = "00000000-0000-0000-0000-00000000b001"
$Aloj = "00000000-0000-0000-0000-0000000a0001"

function ConvertTo-B64Url([byte[]]$bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
function New-Jwt([string]$sub, [string]$rol, [string]$secret) {
    $enc = [Text.Encoding]::UTF8
    $exp = [int64]([DateTime]::UtcNow - [DateTime]'1970-01-01').TotalSeconds + 7200
    $h = ConvertTo-B64Url ($enc.GetBytes('{"alg":"HS256","typ":"JWT"}'))
    $p = ConvertTo-B64Url ($enc.GetBytes('{"sub":"' + $sub + '","rol":"' + $rol + '","exp":' + $exp + '}'))
    $hm = New-Object Security.Cryptography.HMACSHA256
    $hm.Key = $enc.GetBytes($secret)
    $s = ConvertTo-B64Url ($hm.ComputeHash($enc.GetBytes("$h.$p")))
    return "$h.$p.$s"
}
$tokAnf = New-Jwt $Anfitrion "ANFITRION" $Secret
$tokHue = New-Jwt $Huesped "HUESPED" $Secret

function Llamar([string]$metodo, [string]$ruta, $cuerpo, [string]$llave, [string]$token) {
    $cab = @{}
    if ($token) { $cab["Authorization"] = "Bearer $token" }
    if ($llave) { $cab["Idempotency-Key"] = $llave }
    $p = @{ Method = $metodo; Uri = "$BaseUrl$ruta"; Headers = $cab; ContentType = "application/json; charset=utf-8" }
    if ($null -ne $cuerpo) { $p["Body"] = [Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 6)) }
    try {
        $r = Invoke-RestMethod @p
        return @{ ok = $true; status = 200; cuerpo = $r }
    } catch {
        $codigo = [int]$_.Exception.Response.StatusCode
        $lector = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $texto = $lector.ReadToEnd()
        $json = $null
        try { $json = $texto | ConvertFrom-Json } catch { }
        return @{ ok = $false; status = $codigo; cuerpo = $json; texto = $texto }
    }
}

$fallos = 0
function Verificar([string]$caso, [bool]$condicion, [string]$detalle) {
    if ($condicion) { Write-Host ("  PASS  " + $caso) -ForegroundColor Green }
    else { Write-Host ("  FAIL  " + $caso + "  " + $detalle) -ForegroundColor Red; $script:fallos++ }
}

$llegada = (Get-Date).AddDays(80).ToString("yyyy-MM-dd")
$salida = (Get-Date).AddDays(83).ToString("yyyy-MM-dd")
$bloqIni = (Get-Date).AddDays(120).ToString("yyyy-MM-dd")
$bloqFin = (Get-Date).AddDays(125).ToString("yyyy-MM-dd")

Write-Host "HU-008) Disponibilidad"
$r = Llamar "Get" "/api/availability/$Aloj" $null "" ""
Verificar "GET publico sin token -> 200" ($r.ok) ($r.texto)
$r = Llamar "Put" "/api/availability/$Aloj" @{ periodos = @(@{ fechaInicio = $bloqIni; fechaFin = $bloqFin; tipo = "NO_DISPONIBLE" }) } "" $tokHue
Verificar "PUT como huesped -> 403" ((-not $r.ok) -and $r.status -eq 403) ("status " + $r.status)
$r = Llamar "Put" "/api/availability/$Aloj" @{ periodos = @(@{ fechaInicio = $bloqIni; fechaFin = $bloqFin; tipo = "NO_DISPONIBLE" }) } "" $tokAnf
Verificar "PUT como anfitrion propietario -> 200" ($r.ok -and $r.cuerpo.periodos.Count -eq 1) ($r.texto)
$r = Llamar "Put" "/api/availability/$Aloj" @{ periodos = @(@{ fechaInicio = $bloqIni; fechaFin = $bloqFin; tipo = "RESERVADO" }) } "" $tokAnf
Verificar "PUT con tipo RESERVADO -> 422 (regla de negocio)" ((-not $r.ok) -and $r.status -eq 422) ("status " + $r.status)
$cot = Llamar "Post" "/api/quotes" @{ idAlojamiento = $Aloj; fechaLlegada = $bloqIni; fechaSalida = $bloqFin; huespedes = 2 } "" $tokHue
Verificar "cotizar sobre periodo bloqueado -> rechazado" (-not $cot.ok) ("status " + $cot.status)
# dejar la disponibilidad limpia
Llamar "Put" "/api/availability/$Aloj" @{ periodos = @() } "" $tokAnf | Out-Null

Write-Host "HU-014/015/026) Reserva, pago, listados y notificaciones"
$cot = Llamar "Post" "/api/quotes" @{ idAlojamiento = $Aloj; fechaLlegada = $llegada; fechaSalida = $salida; huespedes = 2 } "" $tokHue
Verificar "cotizacion -> 200" ($cot.ok) ($cot.texto)
$res = Llamar "Post" "/api/bookings" @{ tokenCotizacion = $cot.cuerpo.tokenCotizacion } ([guid]::NewGuid().ToString()) $tokHue
Verificar "crear reserva -> PENDIENTE_PAGO" ($res.ok -and $res.cuerpo.estado -eq "PENDIENTE_PAGO") ($res.texto)
$idRes = $res.cuerpo.id
$pago = Llamar "Post" "/api/payments" @{ idReserva = $idRes; metodoPago = "pm_card_visa" } ([guid]::NewGuid().ToString()) $tokHue
Verificar "pago -> CONFIRMADA" ($pago.ok -and $pago.cuerpo.estadoReserva -eq "CONFIRMADA") ($pago.texto)

$r = Llamar "Get" "/api/bookings" $null "" $tokHue
Verificar "GET /api/bookings (huesped) incluye la reserva" ($r.ok -and (@($r.cuerpo.contenido | Where-Object { $_.id -eq $idRes }).Count -eq 1)) ($r.texto)
$r = Llamar "Get" "/api/bookings/received" $null "" $tokAnf
Verificar "GET /api/bookings/received (anfitrion) incluye la reserva" ($r.ok -and (@($r.cuerpo.contenido | Where-Object { $_.id -eq $idRes }).Count -eq 1)) ($r.texto)
$r = Llamar "Get" "/api/bookings/received" $null "" $tokHue
Verificar "received como huesped -> 403" ((-not $r.ok) -and $r.status -eq 403) ("status " + $r.status)
$r = Llamar "Get" "/api/bookings/$idRes" $null "" $tokHue
Verificar "detalle de la reserva propia -> 200" ($r.ok -and $r.cuerpo.id -eq $idRes) ($r.texto)

$n = Llamar "Get" "/api/notifications?unread=true" $null "" $tokHue
Verificar "notificaciones del huesped (reserva creada / pago aprobado)" ($n.ok -and $n.cuerpo.contenido.Count -ge 2) ($n.texto)
if ($n.ok) { $n.cuerpo.contenido | ForEach-Object { Write-Host ("        " + $_.tipo + ": " + $_.mensaje) } }
$na = Llamar "Get" "/api/notifications?unread=true" $null "" $tokAnf
Verificar "notificacion RESERVA_RECIBIDA para el anfitrion" ($na.ok -and (@($na.cuerpo.contenido | Where-Object { $_.tipo -eq "RESERVA_RECIBIDA" }).Count -ge 1)) ($na.texto)
if ($n.ok -and $n.cuerpo.contenido.Count -ge 1) {
    $id1 = $n.cuerpo.contenido[0].id
    $m = Llamar "Patch" "/api/notifications/$id1/read" $null "" $tokHue
    Verificar "marcar como leida -> leida=true" ($m.ok -and $m.cuerpo.leida -eq $true) ($m.texto)
    $m = Llamar "Patch" "/api/notifications/$id1/read" $null "" $tokAnf
    Verificar "marcar notificacion ajena -> rechazado" (-not $m.ok) ("status " + $m.status)
}

# limpieza: cancelar la reserva de prueba
$c = Llamar "Post" "/api/bookings/$idRes/cancel" @{ motivo = "limpieza prueba" } ([guid]::NewGuid().ToString()) $tokHue
Verificar "cancelar reserva de prueba -> CANCELADA" ($c.ok -and $c.cuerpo.estado -like "CANCELADA*") ($c.texto)

Write-Host ""
if ($fallos -eq 0) { Write-Host "TODO OK: 0 fallos" -ForegroundColor Green } else { Write-Host ("$fallos fallos") -ForegroundColor Red }
Write-Host "Para HU-026 (logs): revisa en la consola del backend las lineas JSON con RESERVA_CREADA, PAGO_... y DISPONIBILIDAD_ACTUALIZADA."
