$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dir = Join-Path $env:USERPROFILE 'laudate-pruebas'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$file = Join-Path $dir 'misa-de-prueba.laudate'

# Repertorio minimo valido (formato laudate.event).
$json = @'
{
  "format": "laudate.event",
  "formatVersion": 1,
  "exportedAt": 1759300000000,
  "appVersion": "prueba-desktop",
  "celebration": {
    "name": "Misa de prueba",
    "type": "Misa",
    "dateTimestamp": 1759300000000,
    "description": "Repertorio de prueba de la asociacion .laudate",
    "items": [ { "songIndex": 0, "moment": "Entrada" } ]
  },
  "songs": [
    {
      "title": "Aleluya de prueba",
      "originalKey": "D",
      "contentHash": "sha256:test",
      "chordPro": "{title: Aleluya de prueba}\n{key: D}\n{soc}\n[D]Aleluya, ale[A]luya\n{eoc}\n"
    }
  ]
}
'@
Set-Content -Path $file -Value $json -Encoding UTF8
Write-Host ("Fichero creado: " + $file)

Write-Host '=== Abriendo con la app asociada (equivalente a doble clic) ==='
Start-Process $file
Start-Sleep -Seconds 9

$proc = Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue
if ($proc) {
    $win = $proc | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if ($win) { Write-Host ("OK: Canticora abierto (PID " + $win.Id + ")") }
}
Write-Host 'Comprueba en la ventana de Canticora que aparece la pantalla "Importar" con el dialogo del archivo.'
