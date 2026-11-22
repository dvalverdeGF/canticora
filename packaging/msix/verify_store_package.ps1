# Verifica el MSIX de la Store ANTES de subirlo al Partner Center.
#
#   Uso:  powershell -ExecutionPolicy Bypass -File packaging\msix\verify_store_package.ps1
#
# Comprueba:
#   1. Que el paquete existe.
#   2. Que la IDENTIDAD del manifiesto coincide EXACTAMENTE con el Partner Center
#      (producto 9P6TNMBN6SMZ). Es la causa nº1 de rechazo al subir el paquete.
#   3. Que la arquitectura es x64 y el MinVersion es el esperado.
#   4. Si el paquete va firmado o no (para la Store da igual: lo re-firma Microsoft).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# Toma el MSIX de Store **más reciente**: así no hay que editar el script en cada versión.
$msix = Get-ChildItem (Join-Path $root 'desktopApp\build\compose\binaries\main\msix') -Filter '*-store.msix' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
$makeappx = Join-Path $root '.sdkbt\bin\10.0.26100.0\x64\makeappx.exe'
$makeappx = Join-Path $root '.sdkbt\bin\10.0.26100.0\x64\makeappx.exe'

# --- Identidad esperada (Partner Center) ---
$expectedName      = 'Grafitto.Laudate'
$expectedPublisher = 'CN=F1E0C245-FAB1-4CCA-9015-1150609FDA03'
$expectedMinVer    = '10.0.17763.0'

Write-Host "=== Verificando: $msix ==="
if (-not (Test-Path $msix)) {
    Write-Host "ERROR: no existe el paquete. Ejecuta:  packaging\msix\build_msix.bat store" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $makeappx)) {
    Write-Host "ERROR: falta makeappx.exe en $makeappx" -ForegroundColor Red
    exit 1
}

# --- Extraer el manifiesto sin descomprimir todo ---
$tmp = Join-Path $env:TEMP ('laudchk-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp | Out-Null
& $makeappx unpack /p $msix /d $tmp /o | Out-Null
$manifestPath = Join-Path $tmp 'AppxManifest.xml'
[xml]$m = Get-Content $manifestPath

$name      = $m.Package.Identity.Name
$publisher = $m.Package.Identity.Publisher
$version   = $m.Package.Identity.Version
$arch      = $m.Package.Identity.ProcessorArchitecture
$minVer    = $m.Package.Dependencies.TargetDeviceFamily.MinVersion

Write-Host ""
Write-Host "Identidad del PAQUETE:"
Write-Host "  Name        = $name"
Write-Host "  Publisher   = $publisher"
Write-Host "  Version     = $version"
Write-Host "  Arch        = $arch"
Write-Host "  MinVersion  = $minVer"
Write-Host ""

$ok = $true
if ($name -ne $expectedName) {
    Write-Host "X  Name NO coincide. Se esperaba: $expectedName" -ForegroundColor Red; $ok = $false
} else { Write-Host "OK Name coincide." -ForegroundColor Green }

if ($publisher -ne $expectedPublisher) {
    Write-Host "X  Publisher NO coincide. Se esperaba: $expectedPublisher" -ForegroundColor Red
    Write-Host "   -> Edita packaging\msix\AppxManifest.store.xml y reempaqueta." -ForegroundColor Yellow
    $ok = $false
} else { Write-Host "OK Publisher coincide con el Partner Center." -ForegroundColor Green }

if ($version -notmatch '^1\.') {
    Write-Host "!  Version '$version' distinta de 1.x. Revisa que sea la version real (1.0.21.0)." -ForegroundColor Yellow
}
if ($version -match '\.0$') {
    Write-Host "OK El 4.º digito de Version es 0 (requisito de la Store)." -ForegroundColor Green
} else {
    Write-Host "X  El 4.º digito de Version debe ser 0 (lo gestiona la Store)." -ForegroundColor Red; $ok = $false
}
if ($arch -ne 'x64') { Write-Host "!  Arquitectura '$arch' (se esperaba x64)." -ForegroundColor Yellow }
if ($minVer -ne $expectedMinVer) { Write-Host "!  MinVersion '$minVer' (se esperaba $expectedMinVer)." -ForegroundColor Yellow }

# --- Firma (informativo) ---
$sig = Get-AuthenticodeSignature -FilePath $msix
Write-Host ""
Write-Host "Firma: $($sig.Status)  ($(if ($sig.SignerCertificate) { $sig.SignerCertificate.Subject } else { 'sin firmar' }))"
Write-Host "  (Para la Store da igual: Microsoft re-firma el paquete al certificar.)" -ForegroundColor DarkGray

Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
Write-Host ""
if ($ok) {
    Write-Host "=== LISTO para subir a Partner Center (Paquetes). ===" -ForegroundColor Green
} else {
    Write-Host "=== CORRIGE lo indicado en rojo antes de subir. ===" -ForegroundColor Red
    exit 1
}
