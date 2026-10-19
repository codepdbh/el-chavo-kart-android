# Installs the last built APK on the connected device (adb) and optionally starts the launcher.
#
#   powershell -ExecutionPolicy Bypass -File scripts\install-android.ps1 [-Serial <id>] [-Launch] [-Configuration Debug]
#
# Upgrades in place (adb install -r) so imported game data, drivers and settings are preserved.
param([string]$Serial, [switch]$Launch, [ValidateSet('Debug', 'Release')][string]$Configuration = 'Debug')
. "$PSScriptRoot\common.ps1"
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "[ANDROID] adb not found: $adb" }
$apkDir = Join-Path $ProjectRoot "android\app\build\outputs\apk\$($Configuration.ToLower())"
$apk = Get-ChildItem $apkDir -Filter '*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "[ANDROID] No APK in ${apkDir}: run scripts\build-android.ps1" }
$target = @(); if ($Serial) { $target = @('-s', $Serial) }
$abi = (& $adb @target shell getprop ro.product.cpu.abi).Trim()
if ($abi -ne 'arm64-v8a') { throw "[ANDROID] Device ABI is '$abi'; this port requires arm64-v8a" }
& $adb @target install -r $apk.FullName
if ($LASTEXITCODE -ne 0) { throw "[ANDROID] adb install failed ($LASTEXITCODE)" }
Write-Output "[ANDROID] Installed $($apk.Name) on $((& $adb @target shell getprop ro.product.model).Trim())"
if ($Launch) {
    & $adb @target shell am start -n com.codepdbh.chavokart/.LauncherActivity | Out-Null
    Write-Output '[ANDROID] Launcher started'
}
