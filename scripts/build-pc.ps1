# Builds the Windows x64 diagnostic executable (D3D12 Xenos backend) from the generated code.
#
#   powershell -ExecutionPolicy Bypass -File scripts\build-pc.ps1 [-Jobs 8]
#
# Requires scripts\generate-code.ps1 first. Output: out\pc\chavokart.exe. Log: out\logs\build-pc-<timestamp>.log
param([string]$LlvmBin, [string]$CMakeBin, [int]$Jobs = 8)
. "$PSScriptRoot\common.ps1"
Assert-CkXex (Join-Path $ProjectRoot 'game_xex\default.xex')
if (-not (Test-Path (Join-Path $ProjectRoot 'generated\default\sources.cmake'))) {
    throw '[RECOMP] generated/default is missing: run scripts\generate-code.ps1 first'
}
Enter-CkHostToolchain $LlvmBin $CMakeBin
$build = Join-Path $ProjectRoot 'out\pc'
$logs = Join-Path $ProjectRoot 'out\logs'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not (Test-Path (Join-Path $build 'CMakeCache.txt'))) { throw '[RECOMP] out\pc is not configured: run scripts\generate-code.ps1' }
# Re-run configure so CMake picks up the generated source list.
$rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @($build) "$logs\cmake-reconfigure-$stamp.log"
if ($rc) { throw "[RECOMP] CMake reconfigure failed ($rc): $logs\cmake-reconfigure-$stamp.log" }
$start = Get-Date
$rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @('--build', $build, '--target', 'chavokart', '--parallel', "$Jobs") "$logs\build-pc-$stamp.log"
Get-Content "$logs\build-pc-$stamp.log" -Tail 15
if ($rc) { throw "[RECOMP] PC build failed ($rc): $logs\build-pc-$stamp.log" }
Write-Output ("[RECOMP] PC build finished in {0:N1} min: {1}" -f ((Get-Date) - $start).TotalMinutes, (Join-Path $build 'chavokart.exe'))
