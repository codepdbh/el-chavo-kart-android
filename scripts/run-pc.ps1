# Runs the Windows diagnostic build against the user's copy (read-only) and keeps a timestamped log.
#
#   powershell -ExecutionPolicy Bypass -File scripts\run-pc.ps1 [-Seconds 90] [-DumpImage] [-LogLevel info] [-Extra '--flag=value']
#
# -Seconds > 0 stops the process after that time (automated runs); 0 leaves it running interactively.
# Logs: out\runtime\logs\run-<timestamp>.log. User data/cache: out\runtime\user, out\runtime\cache.
param([int]$Seconds = 0, [switch]$DumpImage, [string]$LogLevel = 'info', [string[]]$Extra = @(), [string]$ScreenshotAt = '')
. "$PSScriptRoot\common.ps1"
$xex = Join-Path $ProjectRoot 'game_xex\default.xex'
Assert-CkXex $xex
$exe = Join-Path $ProjectRoot 'out\pc\chavokart.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw '[RECOMP] Build out\pc\chavokart.exe first with scripts\build-pc.ps1' }
$gameRoot = (Get-Item -LiteralPath (Join-Path $ProjectRoot 'game_xex')).Target
if (-not $gameRoot) { $gameRoot = Join-Path $ProjectRoot 'game_xex' }
if ($gameRoot -is [array]) { $gameRoot = $gameRoot[0] }
$runtime = Join-Path $ProjectRoot 'out\runtime'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force "$runtime\logs", "$runtime\user", "$runtime\cache", "$runtime\metadata" | Out-Null
$log = "$runtime\logs\run-$stamp.log"
$runArgs = @("--game_data_root=`"$gameRoot`"", "--user_data_root=`"$runtime\user`"", "--cache_root=`"$runtime\cache`"",
    "--metadata_root=`"$runtime\metadata`"", "--log_file=`"$log`"", "--log_level=$LogLevel")
if ($DumpImage) { $runArgs += '--chavokart_dump_image=true' }
$runArgs += $Extra
$p = Start-Process -FilePath $exe -ArgumentList $runArgs -WorkingDirectory (Split-Path $exe) -PassThru
Write-Output "[PC] pid $($p.Id) log $log"
$shots = @($ScreenshotAt -split ',' | Where-Object { $_ } | ForEach-Object { [int]$_ })
if ($Seconds -gt 0 -and $shots.Count -gt 0) {
    # Captures only the game window rectangle (not the rest of the desktop) at the requested times.
    Add-Type -AssemblyName System.Drawing
    Add-Type @'
using System; using System.Runtime.InteropServices;
public static class CkWin { [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h); }
'@
    $elapsed = 0
    foreach ($t in ($shots | Sort-Object)) {
        if ($t -ge $Seconds) { break }
        if ($p.WaitForExit(($t - $elapsed) * 1000)) { break }
        $elapsed = $t
        $p.Refresh()
        $h = $p.MainWindowHandle
        if ($h -eq [IntPtr]::Zero) { continue }
        [CkWin]::SetForegroundWindow($h) | Out-Null
        Start-Sleep -Milliseconds 300
        $r = New-Object CkWin+RECT
        [CkWin]::GetWindowRect($h, [ref]$r) | Out-Null
        $bmp = New-Object System.Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.CopyFromScreen($r.L, $r.T, 0, 0, $bmp.Size)
        $shot = "$runtime\logs\run-$stamp-t$t.png"
        $bmp.Save($shot, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
        Write-Output "[PC] screenshot $shot"
    }
    $Seconds = [Math]::Max(1, $Seconds - $elapsed)
}
if ($Seconds -gt 0) {
    if (-not $p.WaitForExit($Seconds * 1000)) {
        Stop-Process -Id $p.Id -Force
        Write-Output "[PC] stopped after $Seconds s (still running: no crash within the window)"
    } else {
        Write-Output ("[PC] process exited with code 0x{0:X8}" -f $p.ExitCode)
    }
    if (Test-Path $log) { Get-Content $log -Tail 40 }
}
