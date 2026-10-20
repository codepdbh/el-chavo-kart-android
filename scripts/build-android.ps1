# Builds the Android APK (arm64-v8a).
#
#   powershell -ExecutionPolicy Bypass -File scripts\build-android.ps1 [-Configuration Debug|Release] [-WithGame]
#
# Default: diagnostic APK (launcher, isolated GPU probe, Vulkan test). -WithGame also compiles the recompiled game
# (generated/default, ARM64) into libmain.so; it is built as Release (the Debug -O0 build of the generated code is
# impractical) and signed with the local debug key for experimental testing.
# Toolchain (verified on the development machine): JDK 17/21 (Android Studio jbr), Android SDK 35, NDK 28.2.13676358,
# SDK CMake 3.30.5, Gradle 8.13 wrapper, AGP 8.9.1. Logs: out\logs\android-<timestamp>.log
param([ValidateSet('Debug', 'Release')][string]$Configuration = 'Debug', [switch]$WithGame, [string]$JavaHome)
. "$PSScriptRoot\common.ps1"
if ($WithGame) {
    Assert-CkXex (Join-Path $ProjectRoot 'game_xex\default.xex')
    if (-not (Test-Path (Join-Path $ProjectRoot 'generated\default\sources.cmake'))) {
        throw '[ANDROID] generated/default is missing: run scripts\generate-code.ps1 first'
    }
    $Configuration = 'Release'
}
if (-not $JavaHome) { $JavaHome = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not (Test-Path "$JavaHome\bin\java.exe")) { throw "[ANDROID] JDK 17/21 not found at $JavaHome (do not use Java 26)" }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
foreach ($p in @("$sdk\platforms\android-35", "$sdk\ndk\28.2.13676358", "$sdk\cmake\3.30.5\bin\cmake.exe",
        (Join-Path $ProjectRoot 'references\nfsmw-android\sdk\thirdparty\sdl3\CMakeLists.txt'))) {
    if (-not (Test-Path -LiteralPath $p)) { throw "[ANDROID] Missing dependency: $p" }
}
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $sdk
$android = Join-Path $ProjectRoot 'android'
# local.properties is machine-specific and ignored by git.
"sdk.dir=$($sdk.Replace('\', '\\'))" | Set-Content -Encoding ASCII (Join-Path $android 'local.properties')
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = Join-Path $ProjectRoot "out\logs\android-$stamp.log"
$task = "assemble$Configuration"
$gradleArgs = @('-p', $android, $task, '--no-daemon', '--console=plain')
if ($WithGame) { $gradleArgs += '-PwithGame' }
$start = Get-Date
$rc = Invoke-CkLogged (Join-Path $android 'gradlew.bat') $gradleArgs $log
Write-Output ("[ANDROID] Gradle finished in {0:N1} min" -f ((Get-Date) - $start).TotalMinutes)
Get-Content $log -Tail 20
if ($rc) { throw "[ANDROID] Gradle $task failed ($rc): $log" }
$apk = Get-ChildItem (Join-Path $android 'app\build\outputs\apk') -Recurse -Filter '*.apk' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Output "[ANDROID] APK: $($apk.FullName) ($([math]::Round($apk.Length / 1MB, 1)) MB)"
