# Prueba en vivo de HU-006 (editar alojamiento) y HU-010 (consultar detalle) contra el backend local.
# Requisitos: V5 aplicada en Supabase, backend corriendo con la rama de S2 (o main con S2 fusionada),
#             anfitrion de prueba a001 existente (el mismo de las pruebas de reservas).
# Uso:  powershell -ExecutionPolicy Bypass -File .\prueba-alojamientos.ps1 -Secret "<el mismo JWT_SECRET del backend>"
param(
    [Parameter(Mandatory = $true)][string]$Secret,
    [string]$BaseUrl = "http://localhost:8080"
)

$Anfitrion = "00000000-0000-0000-0000-00000000a001"
$OtroAnfitrion = "00000000-0000-0000-0000-00000000a002"
$Huesped = "00000000-0000-0000-0000-00000000b001"
$Admin = "00000000-0000-0000-0000-00000000d001"

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
$tokOtro = New-Jwt $OtroAnfitrion "ANFITRION" $Secret
$tokHue = New-Jwt $Huesped "HUESPED" $Secret
$tokAdm = New-Jwt $Admin "ADMINISTRADOR" $Secret

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

# ---------------------------------------------------------------- preparacion
Write-Host "Preparacion) Crear y activar una publicacion de prueba"
$pol = Llamar "Get" "/api/cancellation-policies" $null "" ""
Verificar "GET /api/cancellation-policies (publico) devuelve politicas" ($pol.ok -and @($pol.cuerpo).Count -ge 1) ($pol.texto)
$idPol = @($pol.cuerpo)[0].id

$datos = @{ titulo = "[PRUEBA HU-006/010] Apartamento de prueba"; descripcion = "Apartamento creado por el script de verificacion."
    tipo = "APARTAMENTO"; ciudad = "Bogota"; direccion = "Calle 1 # 2-3"; capacidad = 4; precioPorNoche = 180000
    servicios = @(); reglas = "No fumar"; imagenes = @("https://cdn.ejemplo.com/prueba1.jpg"); idPoliticaCancelacion = $idPol }
$c = Llamar "Post" "/api/accommodations" $datos "" $tokAnf
Verificar "POST /api/accommodations -> 201 en BORRADOR" ($c.ok -and $c.cuerpo.estado -eq "BORRADOR") ($c.texto)
if (-not $c.ok) { Write-Host "No se pudo crear la publicacion; se detiene la prueba." -ForegroundColor Red; exit 1 }
$id = $c.cuerpo.id
$a = Llamar "Patch" "/api/accommodations/$id/status" @{ estado = "ACTIVA" } "" $tokAnf
Verificar "activar la publicacion -> ACTIVA" ($a.ok -and $a.cuerpo.estadoNuevo -eq "ACTIVA") ($a.texto)

# ---------------------------------------------------------------- HU-010
Write-Host "HU-010) Consultar detalle (publico)"
$d = Llamar "Get" "/api/accommodations/$id" $null "" ""
Verificar "CA-01 detalle sin token -> 200 con titulo, precio, imagenes y politica" ($d.ok -and $d.cuerpo.titulo -like "*PRUEBA*" -and $d.cuerpo.precioPorNoche -eq 180000 -and @($d.cuerpo.imagenes).Count -eq 1 -and $null -ne $d.cuerpo.politicaCancelacion) ($d.texto)
Verificar "CA-02 sin resenas -> totalResenas = 0" ($d.ok -and $d.cuerpo.totalResenas -eq 0) ($d.texto)
$d = Llamar "Get" "/api/accommodations/00000000-0000-0000-0000-0000000f0f0f" $null "" ""
Verificar "CA-03 alojamiento inexistente -> 404" ((-not $d.ok) -and $d.status -eq 404) ("status " + $d.status)

# ---------------------------------------------------------------- HU-006
Write-Host "HU-006) Editar alojamiento"
$edit = $datos.Clone(); $edit.capacidad = 5; $edit.precioPorNoche = 200000
$r = Llamar "Put" "/api/accommodations/$id" $edit "" $tokAnf
Verificar "CA-01 editar capacidad y precio -> 200 con los cambios" ($r.ok -and $r.cuerpo.capacidad -eq 5 -and $r.cuerpo.precioPorNoche -eq 200000) ($r.texto)
Verificar "CA-01 un cambio no critico deja la publicacion ACTIVA" ($r.ok -and $r.cuerpo.estado -eq "ACTIVA") ($r.texto)

$mal = $datos.Clone(); $mal.capacidad = 0; $mal.precioPorNoche = -5
$r = Llamar "Put" "/api/accommodations/$id" $mal "" $tokAnf
Verificar "CA-02 capacidad 0 y precio negativo -> 400 con campos a corregir" ((-not $r.ok) -and $r.status -eq 400 -and @($r.cuerpo.detalles).Count -ge 2) ($r.texto)
$mal = $datos.Clone(); $mal.titulo = ""
$r = Llamar "Put" "/api/accommodations/$id" $mal "" $tokAnf
Verificar "CA-02 borrar el titulo de una ACTIVA -> 400" ((-not $r.ok) -and $r.status -eq 400) ($r.texto)
$mal = $datos.Clone(); $mal.imagenes = @()
$r = Llamar "Put" "/api/accommodations/$id" $mal "" $tokAnf
Verificar "CA-02 quitar todas las imagenes de una ACTIVA -> 400" ((-not $r.ok) -and $r.status -eq 400) ($r.texto)
$mal = $datos.Clone(); $mal.idPoliticaCancelacion = $null
$r = Llamar "Put" "/api/accommodations/$id" $mal "" $tokAnf
Verificar "CA-02 quitar la politica de una ACTIVA -> 400 (no 500)" ((-not $r.ok) -and $r.status -eq 400) ("status " + $r.status + " " + $r.texto)

$r = Llamar "Put" "/api/accommodations/$id" $edit "" $tokOtro
Verificar "CA-03 otro anfitrion intenta editar -> 403" ((-not $r.ok) -and $r.status -eq 403) ("status " + $r.status)
$r = Llamar "Put" "/api/accommodations/$id" $edit "" $tokHue
Verificar "CA-04 huesped intenta editar -> 403" ((-not $r.ok) -and $r.status -eq 403) ("status " + $r.status)

$adm = $edit.Clone(); $adm.reglas = "Regla ajustada por el administrador"
$r = Llamar "Put" "/api/accommodations/$id" $adm "" $tokAdm
Verificar "CA-05 administrador edita -> 200 (revisa en el log del backend PUBLICACION_EDITADA_POR_ADMIN)" ($r.ok -and $r.cuerpo.reglas -like "*administrador*") ($r.texto)

# CA-07: reserva confirmada y luego cambio de precio
Write-Host "CA-07) Las reservas confirmadas conservan su precio"
$ini = (Get-Date).AddDays(90).ToString("yyyy-MM-dd"); $fin = (Get-Date).AddDays(92).ToString("yyyy-MM-dd")
$cot = Llamar "Post" "/api/quotes" @{ idAlojamiento = $id; fechaLlegada = $ini; fechaSalida = $fin; huespedes = 2 } "" $tokHue
$res = Llamar "Post" "/api/bookings" @{ tokenCotizacion = $cot.cuerpo.tokenCotizacion } ([guid]::NewGuid().ToString()) $tokHue
$pago = Llamar "Post" "/api/payments" @{ idReserva = $res.cuerpo.id; metodoPago = "pm_card_visa" } ([guid]::NewGuid().ToString()) $tokHue
Verificar "reserva confirmada de prueba" ($pago.ok -and $pago.cuerpo.estadoReserva -eq "CONFIRMADA") ($pago.texto)
if ($pago.ok) {
    $totalAntes = $res.cuerpo.desglose.total
    $edit.precioPorNoche = 300000
    $r = Llamar "Put" "/api/accommodations/$id" $edit "" $tokAnf
    Verificar "cambiar el precio -> 200" ($r.ok -and $r.cuerpo.precioPorNoche -eq 300000) ($r.texto)
    $b = Llamar "Get" "/api/bookings/$($res.cuerpo.id)" $null "" $tokHue
    Verificar "la reserva confirmada mantiene su total" ($b.ok -and $b.cuerpo.desglose.total -eq $totalAntes) ($b.texto)
    $c2 = Llamar "Post" "/api/bookings/$($res.cuerpo.id)/cancel" @{ motivo = "limpieza prueba" } ([guid]::NewGuid().ToString()) $tokHue
    Verificar "cancelar la reserva de prueba" ($c2.ok) ($c2.texto)
}

# CA-06 y HU-010 CA-03 con una publicacion no activa
Write-Host "CA-06) Cambio critico en una publicacion ACTIVA"
$crit = $edit.Clone(); $crit.titulo = "[PRUEBA HU-006/010] Titulo editado"
$r = Llamar "Put" "/api/accommodations/$id" $crit "" $tokAnf
Verificar "cambiar el titulo -> 200 y estado PENDIENTE_REVISION" ($r.ok -and $r.cuerpo.estado -eq "PENDIENTE_REVISION") ($r.texto)
$d = Llamar "Get" "/api/accommodations/$id" $null "" ""
Verificar "HU-010 CA-03 publicacion no activa -> 404 en el detalle publico" ((-not $d.ok) -and $d.status -eq 404) ("status " + $d.status)
$m = Llamar "Get" "/api/accommodations/mine/$id" $null "" $tokAnf
Verificar "el anfitrion si la ve en /mine/{id}" ($m.ok -and $m.cuerpo.estado -eq "PENDIENTE_REVISION") ($m.texto)

Write-Host ""
if ($fallos -eq 0) { Write-Host "TODO OK: 0 fallos" -ForegroundColor Green } else { Write-Host ("$fallos fallos") -ForegroundColor Red }
Write-Host "Queda una publicacion de prueba '[PRUEBA HU-006/010]' en PENDIENTE_REVISION; se puede borrar en Supabase (catalogo.alojamiento)."
