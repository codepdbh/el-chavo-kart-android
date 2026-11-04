# Runs the Windows diagnostic build against the user's copy (read-only) and keeps a timestamped log.
#
#   powershell -ExecutionPolicy Bypass -File scripts\run-pc.ps1 [-Seconds 90] [-DumpImage] [-LogLevel info] [-Extra '--flag=value']
#
# -Seconds > 0 stops the process after that time (automated runs); 0 leaves it running interactively.
# -Script "100:tap:RETURN;104:tap:SPACE;120:down:E;140:shot;150:up:E" sends keys to the game window (MnK
# emulation: Return=START, Space=A, E=right trigger, Q=left trigger, A/D=steer) and takes window screenshots.
# Keyboard (interactive play): Enter=START, Space=A (jump/accept), Backspace=B (boost/back), L=X (item), P=Y (look
# back), E=RT (gas), Q=LT (brake), A/D=steer, W/S=stick up/down, Shift+arrows=D-pad, Tab=BACK, 1/3=LB/RB.
# Logs: out\runtime\logs\run-<timestamp>.log. User data/cache: out\runtime\user, out\runtime\cache.
param([int]$Seconds = 0, [switch]$DumpImage, [string]$LogLevel = 'info', [string[]]$Extra = @(), [string]$ScreenshotAt = '', [string]$Script = '', [switch]$Vulkan)
. "$PSScriptRoot\common.ps1"
$xex = Join-Path $ProjectRoot 'game_xex\default.xex'
Assert-CkXex $xex
$exe = Join-Path $ProjectRoot $(if ($Vulkan) { 'out\pc-vk\chavokart.exe' } else { 'out\pc\chavokart.exe' })
if (-not (Test-Path -LiteralPath $exe)) { throw '[RECOMP] Build out\pc\chavokart.exe first with scripts\build-pc.ps1' }
$gameRoot = (Get-Item -LiteralPath (Join-Path $ProjectRoot 'game_xex')).Target
if (-not $gameRoot) { $gameRoot = Join-Path $ProjectRoot 'game_xex' }
if ($gameRoot -is [array]) { $gameRoot = $gameRoot[0] }
$runtime = Join-Path $ProjectRoot 'out\runtime'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force "$runtime\logs", "$runtime\user", "$runtime\cache", "$runtime\metadata" | Out-Null
$log = "$runtime\logs\run-$stamp.log"
$runArgs = @("--game_data_root=`"$gameRoot`"", "--user_data_root=`"$runtime\user`"", "--cache_root=`"$runtime\cache`"",
    "--metadata_root=`"$runtime\metadata`"", "--log_file=`"$log`"", "--log_level=$LogLevel",
    # Keyboard as an Xbox pad (MnK emulation) and a window that never covers the desktop.
    '--mnk_mode=true', '--fullscreen=false')
# Automated runs use a small window; interactive play a 1280x720 one.
if ($Seconds -gt 0) { $runArgs += '--window_width=640', '--window_height=360' }
else { $runArgs += '--window_width=1280', '--window_height=720' }
if ($DumpImage) { $runArgs += '--chavokart_dump_image=true' }
$runArgs += $Extra
$p = Start-Process -FilePath $exe -ArgumentList $runArgs -WorkingDirectory (Split-Path $exe) -PassThru
Write-Output "[PC] pid $($p.Id) log $log"
$events = @()
foreach ($t in @($ScreenshotAt -split ',' | Where-Object { $_ })) { $events += [pscustomobject]@{ T = [double]$t; Kind = 'shot'; Key = '' } }
foreach ($e in @($Script -split ';' | Where-Object { $_ })) {
    $parts = $e.Split(':'); $events += [pscustomobject]@{ T = [double]$parts[0]; Kind = $parts[1]; Key = $(if ($parts.Count -gt 2) { $parts[2] } else { '' }) }
}
if ($Seconds -gt 0 -and $events.Count -gt 0) {
    # Captures only the game window, in the background, at the requested times.
    Add-Type -AssemblyName System.Drawing
    Add-Type @'
using System; using System.Runtime.InteropServices;
public static class CkWin { [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type); }
'@
    $elapsed = 0
    $vk = @{ RETURN = 0x0D; SPACE = 0x20; BACK = 0x08; TAB = 0x09; E = 0x45; Q = 0x51; A = 0x41; D = 0x44; W = 0x57; S = 0x53; L = 0x4C; P = 0x50; UP = 0x26; DOWN = 0x28; LEFT = 0x25; RIGHT = 0x27 }
    foreach ($ev in ($events | Sort-Object T)) {
        if ($ev.T -ge $Seconds) { break }
        if ($p.WaitForExit([int](($ev.T - $elapsed) * 1000))) { break }
        $elapsed = $ev.T
        $p.Refresh()
        $h = $p.MainWindowHandle
        if ($h -eq [IntPtr]::Zero) { continue }
        if ($ev.Kind -eq 'shot') {
            Start-Sleep -Milliseconds 200
            $r = New-Object CkWin+RECT
            [CkWin]::GetWindowRect($h, [ref]$r) | Out-Null
            $bmp = New-Object System.Drawing.Bitmap ($r.R - $r.L), ($r.B - $r.T)
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            # PrintWindow with PW_RENDERFULLCONTENT: captures the game window without bringing it to the front.
            $hdc = $g.GetHdc(); [CkWin]::PrintWindow($h, $hdc, 2) | Out-Null; $g.ReleaseHdc($hdc)
            $shot = Join-Path $runtime ("logs" + [IO.Path]::DirectorySeparatorChar + "run-$stamp-t$($ev.T).png")
            $bmp.Save($shot, [System.Drawing.Imaging.ImageFormat]::Png)
            $g.Dispose(); $bmp.Dispose()
            Write-Output "[PC] screenshot $shot"
        } else {
            $code = [byte]$vk[$ev.Key]
            $scan = [byte][CkWin]::MapVirtualKey($code, 0)
            # Window messages to the game window only (WM_KEYDOWN/WM_KEYUP): no global keystrokes, no focus change.
            $down = [IntPtr](1 -bor ([int]$scan -shl 16)); $up = [IntPtr]((1 -bor ([int]$scan -shl 16)) -bor 0xC0000000)
            if ($ev.Kind -ne 'up') { [CkWin]::PostMessage($h, 0x0100, [IntPtr][int]$code, $down) | Out-Null }
            if ($ev.Kind -eq 'tap') { Start-Sleep -Milliseconds 120 }
            if ($ev.Kind -ne 'down') { [CkWin]::PostMessage($h, 0x0101, [IntPtr][int]$code, $up) | Out-Null }
        }
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
