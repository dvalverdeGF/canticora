# Instala Canticora (MSIX de DESARROLLO) en el equipo local del usuario.
#
#   Uso:  powershell -ExecutionPolicy Bypass -File packaging\msix\install_local.ps1
#
# A diferencia de install_msix_admin.ps1, NO pide UAC: confía en el certificado de
# desarrollo solo para el USUARIO actual (Cert:\CurrentUser\Root), que es suficiente
# para instalar un paquete firmado por ese certificado.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pfx  = Join-Path $PSScriptRoot 'LaudateDev.pfx'
# El MSIX de DESARROLLO (sin sufijo `-store`), sea cual sea la version: el nombre lleva
# `canticora.version` y no queremos editarlo en cada release (ADR-022).
$msix = Get-ChildItem (Join-Path $root 'desktopApp\build\compose\binaries\main\msix') -Filter '*.msix' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-store*' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $msix) {
    throw "No hay ningun MSIX de desarrollo. Ejecuta: packaging\msix\build_msix.bat"
}
$pwd  = ConvertTo-SecureString 'laudate' -AsPlainText -Force

Write-Host '=== [1/3] Confiando en el certificado de desarrollo (usuario actual) ==='
Import-PfxCertificate -FilePath $pfx -CertStoreLocation Cert:\CurrentUser\Root -Password $pwd | Out-Null
Write-Host '    Certificado CN=LaudateDev instalado en CurrentUser\Root.'

Write-Host '=== [2/3] Quitando instancia anterior e instalando ==='
Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue | Stop-Process -Force
Get-AppxPackage -Name 'Grafitto.Laudate' | Remove-AppxPackage -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

if (-not (Test-Path $msix)) { throw "No existe el MSIX: $msix" }
Add-AppxPackage -Path $msix -ForceApplicationShutdown

$pkg = Get-AppxPackage -Name 'Grafitto.Laudate'
Write-Host ("    Instalado: " + $pkg.PackageFullName + "  v" + $pkg.Version)

Write-Host '=== [3/3] Lanzando ==='
$app = (Get-AppxPackageManifest $pkg).Package.Applications.Application.Id
Start-Process ("shell:AppsFolder\" + $pkg.PackageFamilyName + "!" + $app)

# La primera apertura (abrir la BD) puede tardar unos segundos.
$p = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 2
    $p = Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($p) { break }
}
if ($p) { Write-Host ("OK: ventana abierta (PID " + $p.Id + ")") } else { Write-Host 'La app no arranco.' }
