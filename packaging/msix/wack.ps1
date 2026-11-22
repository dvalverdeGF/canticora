# Ejecuta el Windows App Certification Kit (WACK) sobre el MSIX de la Store.
# Microsoft recomienda pasarlo ANTES de subir el paquete: detecta los problemas
# que más fallos de certificación provocan.
#
#   Uso:  powershell -ExecutionPolicy Bypass -File packaging\msix\wack.ps1
#
# Nota: el WACK debe ejecutarse en Windows 10/11. Busca `appcert.exe` en las
# rutas habituales del Windows SDK (no requiere admin).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# Toma el MSIX de Store **más reciente**: así no hay que editar el script en cada versión.
$msix = Get-ChildItem (Join-Path $root 'desktopApp\build\compose\binaries\main\msix') -Filter '*-store.msix' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $msix) { Write-Host 'ERROR: no hay ningun MSIX de Store. Ejecuta: packaging\msix\build_msix.bat store' -ForegroundColor Red; exit 1 }
$report = Join-Path $PSScriptRoot 'wack-report.xml'

$candidates = @(
    "$env:ProgramFiles\Windows Kits\10\App Certification Kit\appcert.exe",
    "${env:ProgramFiles(x86)}\Windows Kits\10\App Certification Kit\appcert.exe"
)
$appcert = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $appcert) {
    Write-Host 'ERROR: no encuentro appcert.exe. Instala el Windows SDK (App Certification Kit).' -ForegroundColor Red
    Write-Host 'Alternativa: abre "Windows App Cert Kit" desde el menu Inicio y elige el paquete a mano.'
    exit 1
}
if (-not (Test-Path $msix)) {
    Write-Host "ERROR: no existe el paquete. Ejecuta:  packaging\msix\build_msix.bat store" -ForegroundColor Red
    exit 1
}

Write-Host "=== Ejecutando WACK sobre: $msix ===" -ForegroundColor Cyan
& $appcert reset
& $appcert test -appxpackagepath $msix -reportoutputpath $report

Write-Host ''
Write-Host "Informe: $report" -ForegroundColor Cyan
Write-Host 'Revisa el informe y corrige los avisos antes de subir el paquete.'
