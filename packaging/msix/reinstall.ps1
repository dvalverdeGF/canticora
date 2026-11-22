$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# MSIX de DESARROLLO mas reciente (ADR-022: el nombre del paquete lleva la version).
$msix = Get-ChildItem (Join-Path $root 'desktopApp\build\compose\binaries\main\msix') -Filter '*.msix' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-store*' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $msix) { Write-Host 'ERROR: no hay ningun MSIX de desarrollo.'; exit 1 }

Write-Host '=== Quitando instancia anterior ==='
Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue | Stop-Process -Force
Get-AppxPackage -Name 'Grafitto.Laudate' | Remove-AppxPackage -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host '=== Instalando ==='
Add-AppxPackage -Path $msix -ForceApplicationShutdown
$pkg = Get-AppxPackage -Name 'Grafitto.Laudate'
Write-Host ("    " + $pkg.PackageFullName + "  v" + $pkg.Version)

Write-Host '=== Lanzando ==='
$app = (Get-AppxPackageManifest $pkg).Package.Applications.Application.Id
Start-Process ("shell:AppsFolder\" + $pkg.PackageFamilyName + "!" + $app)

# La primera apertura (crear/abrir la BD y sembrar categorias) puede tardar mas de 8 s.
# Se espera en bucle hasta 40 s a que aparezca una ventana.
$p = $null
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 2
    $p = Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($p) { break }
}
if ($p) { Write-Host ("OK: ventana (PID " + $p.Id + ")") } else { Write-Host 'La app no arranco.' }
