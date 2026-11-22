# Instala Canticora (MSIX) requiriendo permisos de administrador SOLO para confiar en el
# certificado de desarrollo a nivel de máquina. Se autoeleva (aparecerá el aviso de UAC).
#
#   Uso:  doble clic, o "Ejecutar con PowerShell".
#         También: powershell -ExecutionPolicy Bypass -File packaging\msix\install_msix_admin.ps1

$ErrorActionPreference = 'Stop'
$log = Join-Path $PSScriptRoot 'install.log'
function Log($m) { $m | Tee-Object -FilePath $log -Append }

# --- Autoelevación ---
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Remove-Item $log -ErrorAction SilentlyContinue
    Log 'Solicitando permisos de administrador...'
    Start-Process powershell -Verb RunAs -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`""
    )
    exit
}

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pfx  = Join-Path $PSScriptRoot 'LaudateDev.pfx'
# MSIX de DESARROLLO mas reciente (ADR-022: el nombre lleva la version, no se escribe aqui).
$msix = Get-ChildItem (Join-Path $root 'desktopApp\build\compose\binaries\main\msix') -Filter '*.msix' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-store*' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $msix) { Log 'ERROR: no hay ningun MSIX de desarrollo. Ejecuta: packaging\msix\build_msix.bat'; exit 4 }
$pwd  = ConvertTo-SecureString 'laudate' -AsPlainText -Force

try {
    Log '=== [1/3] Confiando en el certificado (a nivel de máquina, requiere admin) ==='
    Import-PfxCertificate -FilePath $pfx -CertStoreLocation Cert:\LocalMachine\Root         -Password $pwd | Out-Null
    Import-PfxCertificate -FilePath $pfx -CertStoreLocation Cert:\LocalMachine\TrustedPeople -Password $pwd | Out-Null
    Log '    Certificado CN=LaudateDev instalado en LocalMachine\Root y TrustedPeople.'

    Log '=== [2/3] Instalando el paquete MSIX ==='
    if (-not (Test-Path $msix)) { throw "No existe el MSIX: $msix" }
    Add-AppxPackage -Path $msix -ForceApplicationShutdown
    Log '    Paquete instalado.'

    Log '=== [3/3] Comprobación ==='
    $pkg = Get-AppxPackage -Name 'Grafitto.Laudate'
    Log ("    " + $pkg.PackageFullName)
    Log 'LISTO. Busca "Canticora" en el menú Inicio.'
} catch {
    Log ("ERROR: " + $_.Exception.Message)
} finally {
    Log ''
    Log 'Esta ventana se cerrará en 15 s. El log queda en packaging\msix\install.log'
    Start-Sleep -Seconds 15
}
