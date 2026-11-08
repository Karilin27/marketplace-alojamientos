# Prueba en vivo de HU-004 (perfil y cuenta) contra el backend local conectado a Supabase.
# Requisito: crear antes el usuario de prueba (ver docs/hu-004-perfil.md, sección "Preparar la prueba").
# Uso:  powershell -ExecutionPolicy Bypass -File .\prueba-perfil.ps1 -Secret "<el mismo JWT_SECRET del backend>"
param(
    [Parameter(Mandatory = $true)][string]$Secret,
    [string]$BaseUrl = "http://localhost:8080"
)

$Usuario = "00000000-0000-0000-0000-00000000c001"

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
$token = New-Jwt $Usuario "HUESPED" $Secret

# Devuelve @{ ok; status; cuerpo }. No lanza excepciones: así se pueden probar también los errores esperados.
function Llamar([string]$metodo, [string]$ruta, $cuerpo, [string]$llave) {
    $cab = @{ Authorization = "Bearer $token" }
    if ($llave) { $cab["Idempotency-Key"] = $llave }
    $p = @{ Method = $metodo; Uri = "$BaseUrl$ruta"; Headers = $cab; ContentType = "application/json; charset=utf-8" }
    if ($null -ne $cuerpo) { $p["Body"] = [Text.Encoding]::UTF8.GetBytes(($cuerpo | ConvertTo-Json -Depth 5)) }
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

Write-Host "CA-04) Sin token no se puede consultar ningún perfil"
try { Invoke-RestMethod -Uri "$BaseUrl/api/users/me" | Out-Null; Verificar "sin token -> 401" $false "respondió 200" }
catch { Verificar "sin token -> 401" ([int]$_.Exception.Response.StatusCode -eq 401) "otro código" }

Write-Host "CA-01) Consultar el perfil propio"
$r = Llamar "Get" "/api/users/me" $null ""
Verificar "GET /api/users/me -> 200" ($r.ok -and $r.cuerpo.id -eq $Usuario) ($r.texto)
if ($r.ok) { Write-Host ("        nombre: " + $r.cuerpo.nombre + " | correo: " + $r.cuerpo.correo + " | estado: " + $r.cuerpo.estado) }

Write-Host "CA-02) Actualizar con datos válidos"
$r = Llamar "Put" "/api/users/me" @{ nombre = "Huésped de pruebas HU-004"; telefono = "+57 300 123 4567"
    documento = "CC-1020304050"; pais = "Colombia"; fotoUrl = "https://cdn.ejemplo.com/foto.png" } ""
Verificar "PUT /api/users/me -> 200 con datos guardados" ($r.ok -and $r.cuerpo.pais -eq "Colombia" -and $r.cuerpo.telefono -eq "+57 300 123 4567") ($r.texto)
$r = Llamar "Get" "/api/users/me" $null ""
Verificar "GET posterior devuelve lo guardado" ($r.ok -and $r.cuerpo.documento -eq "CC-1020304050") ($r.texto)

Write-Host "CA-03) Validación: nombre vacío y teléfono inválido"
$r = Llamar "Put" "/api/users/me" @{ nombre = "  "; telefono = "12"; documento = ""; pais = ""; fotoUrl = "" } ""
Verificar "PUT inválido -> 400 con los campos a corregir" ((-not $r.ok) -and $r.status -eq 400 -and $r.cuerpo.detalles.Count -eq 2) ($r.texto)
if ($r.cuerpo) { $r.cuerpo.detalles | ForEach-Object { Write-Host ("        " + $_) } }

Write-Host "CA-06) Con una reserva confirmada futura no se puede desactivar"
$cot = Llamar "Post" "/api/quotes" @{ idAlojamiento = "00000000-0000-0000-0000-0000000a0002"
    fechaLlegada = (Get-Date).AddDays(50).ToString("yyyy-MM-dd"); fechaSalida = (Get-Date).AddDays(52).ToString("yyyy-MM-dd")
    huespedes = 2 } ""
$res = Llamar "Post" "/api/bookings" @{ tokenCotizacion = $cot.cuerpo.tokenCotizacion } ([guid]::NewGuid().ToString())
$pago = Llamar "Post" "/api/payments" @{ idReserva = $res.cuerpo.id; metodoPago = "pm_card_visa" } ([guid]::NewGuid().ToString())
Verificar "reserva confirmada de prueba" ($pago.ok -and $pago.cuerpo.estadoReserva -eq "CONFIRMADA") ($pago.texto)
$r = Llamar "Patch" "/api/users/me/status" @{ estado = "DESACTIVADO" } ""
Verificar "PATCH DESACTIVADO -> 422 RESERVAS_CONFIRMADAS_PENDIENTES" ((-not $r.ok) -and $r.status -eq 422 -and $r.cuerpo.codigo -eq "RESERVAS_CONFIRMADAS_PENDIENTES") ($r.texto)

Write-Host "CA-05) Sin reservas confirmadas futuras sí se puede desactivar"
$can = Llamar "Post" "/api/bookings/$($res.cuerpo.id)/cancel" @{ motivo = "Prueba HU-004" } ([guid]::NewGuid().ToString())
Verificar "se cancela la reserva de prueba" ($can.ok -and $can.cuerpo.estado -eq "CANCELADA") ($can.texto)
$r = Llamar "Patch" "/api/users/me/status" @{ estado = "DESACTIVADO" } ""
Verificar "PATCH DESACTIVADO -> 200 con sesionCerrada" ($r.ok -and $r.cuerpo.estado -eq "DESACTIVADO" -and $r.cuerpo.sesionCerrada) ($r.texto)

Write-Host "CA-07) Reactivar la cuenta"
$r = Llamar "Patch" "/api/users/me/status" @{ estado = "ACTIVO" } ""
Verificar "PATCH ACTIVO -> 200" ($r.ok -and $r.cuerpo.estado -eq "ACTIVO" -and -not $r.cuerpo.sesionCerrada) ($r.texto)
$r = Llamar "Patch" "/api/users/me/status" @{ estado = "INACTIVO" } ""
Verificar "estado inválido -> 400" ((-not $r.ok) -and $r.status -eq 400) ($r.texto)

Write-Host ""
if ($fallos -eq 0) { Write-Host "Todo correcto." -ForegroundColor Green } else { Write-Host "$fallos caso(s) fallaron." -ForegroundColor Red }
