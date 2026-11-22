param([int]$X, [int]$Y)
$ErrorActionPreference = 'Continue'
Add-Type @"
using System; using System.Runtime.InteropServices;
public class MClick {
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, IntPtr e);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  public const uint LEFTDOWN = 0x0002, LEFTUP = 0x0004;
}
"@

# Traer la ventana al frente
$p = Get-Process -Name 'Canticora' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
if (-not $p) { Write-Host 'NO_WINDOW'; exit 1 }
[MClick]::ShowWindow($p.MainWindowHandle, 9) | Out-Null
[MClick]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 800

[MClick]::SetCursorPos($X, $Y) | Out-Null
Start-Sleep -Milliseconds 250
[MClick]::mouse_event([MClick]::LEFTDOWN, 0, 0, 0, [IntPtr]::Zero)
Start-Sleep -Milliseconds 120
[MClick]::mouse_event([MClick]::LEFTUP, 0, 0, 0, [IntPtr]::Zero)
Write-Host ("Clic en (" + $X + "," + $Y + ")")
