$ErrorActionPreference = 'Continue'
Add-Type @"
using System; using System.Runtime.InteropServices;
public class W {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
}
"@
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
# Baja el cursor al borde inferior para desplegar la barra de tareas (aunque esté auto-oculta).
[W]::SetCursorPos([int]($b.Width/2), $b.Height - 1) | Out-Null
Start-Sleep -Milliseconds 1200
$bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
([System.Drawing.Graphics]::FromImage($bmp)).CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
$bmp.Save((Join-Path $PSScriptRoot 'laudate-taskbar.png'), [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host 'Captura guardada.'
