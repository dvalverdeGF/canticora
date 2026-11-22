$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# MSIX de DESARROLLO mas reciente (ADR-022: el nombre del paquete lleva la version).
$msix = Get-ChildItem (Join-Path $root 'desktopApp\build\compose\binaries\main\msix') -Filter '*.msix' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-store*' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $msix) { Write-Host 'ERROR: no hay ningun MSIX de desarrollo.'; exit 1 }

Write-Host '=== Desinstalando version anterior (si existe) ==='
Get-AppxPackage -Name 'Grafitto.Laudate' | Remove-AppxPackage -ErrorAction SilentlyContinue

Write-Host '=== Instalando el MSIX actualizado ==='
Add-AppxPackage -Path $msix -ForceApplicationShutdown
$pkg = Get-AppxPackage -Name 'Grafitto.Laudate'
Write-Host ("    " + $pkg.PackageFullName)

Write-Host '=== Lanzando ==='
$app = (Get-AppxPackageManifest $pkg).Package.Applications.Application.Id
Start-Process ("shell:AppsFolder\" + $pkg.PackageFamilyName + "!" + $app)
Start-Sleep -Seconds 8
$proc = Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue
if ($proc) {
    $win = $proc | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($win) { Write-Host ("OK: ventana '" + $win.MainWindowTitle + "' (PID " + $win.Id + ")") }
    else { Write-Host ("Proceso vivo (PID " + ($proc.Id -join ',') + ")") }
} else {
    Write-Host 'FALLO: la app no arranco.'
}
