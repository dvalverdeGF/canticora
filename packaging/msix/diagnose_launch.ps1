$ErrorActionPreference = 'Continue'

Write-Host '=== Paquete instalado ==='
$p = Get-AppxPackage -Name 'Grafitto.Laudate'
Write-Host ("    " + $p.PackageFullName + "  v" + $p.Version)

Write-Host '=== Lanzando ==='
$app = (Get-AppxPackageManifest $p).Package.Applications.Application.Id
Start-Process ("shell:AppsFolder\" + $p.PackageFamilyName + "!" + $app)
Start-Sleep -Seconds 15

Write-Host '=== Procesos Canticora ==='
$pr = Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue
if ($pr) {
    $pr | ForEach-Object { Write-Host ("    PID " + $_.Id + "  MainWindow=" + $_.MainWindowHandle + "  Responding=" + $_.Responding) }
} else {
    Write-Host '    NO_HAY_PROCESO (la app ha salido o no ha arrancado)'
}

Write-Host '=== Eventos recientes de la app (Application log) ==='
Get-WinEvent -LogName Application -MaxEvents 40 -ErrorAction SilentlyContinue |
    Where-Object { $_.Message -match 'Canticora|laudate' } |
    Select-Object -First 5 |
    ForEach-Object { Write-Host ('--- ' + $_.TimeCreated + ' [' + $_.LevelDisplayName + '] ' + $_.ProviderName); Write-Host ($_.Message.Substring(0, [Math]::Min(700, $_.Message.Length))) }
