# Builds the Windows x64 diagnostic executable (D3D12 Xenos backend) from the generated code.
#
#   powershell -ExecutionPolicy Bypass -File scripts\build-pc.ps1 [-Jobs 8]
#
# Requires scripts\generate-code.ps1 first. Output: out\pc\chavokart.exe. Log: out\logs\build-pc-<timestamp>.log
param([string]$LlvmBin, [string]$CMakeBin, [int]$Jobs = 8, [switch]$Vulkan)
. "$PSScriptRoot\common.ps1"
Assert-CkXex (Join-Path $ProjectRoot 'game_xex\default.xex')
if (-not (Test-Path (Join-Path $ProjectRoot 'generated\default\sources.cmake'))) {
    throw '[RECOMP] generated/default is missing: run scripts\generate-code.ps1 first'
}
Enter-CkHostToolchain $LlvmBin $CMakeBin
$build = Join-Path $ProjectRoot $(if ($Vulkan) { 'out\pc-vk' } else { 'out\pc' })
$logs = Join-Path $ProjectRoot 'out\logs'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$root = $ProjectRoot.Replace('\', '/')
if (-not (Test-Path (Join-Path $build 'CMakeCache.txt'))) {
    $vk = if ($Vulkan) { 'ON' } else { 'OFF' }
    $rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @('-S', $root, '-B', $build.Replace('\', '/'), '-G', 'Ninja',
        "-DCMAKE_MAKE_PROGRAM=$($script:CMakeBin.Replace('\','/'))/ninja.exe",
        "-DCMAKE_C_COMPILER=$($script:LlvmBin.Replace('\','/'))/clang.exe",
        "-DCMAKE_CXX_COMPILER=$($script:LlvmBin.Replace('\','/'))/clang++.exe", '-DCMAKE_BUILD_TYPE=Release',
        "-DCK_PC_VULKAN=$vk", '-DCMAKE_C_FLAGS=-march=x86-64-v2', '-DCMAKE_CXX_FLAGS=-march=x86-64-v2') "$logs\cmake-configure-$stamp.log"
} else {
    # Re-run configure so CMake picks up the generated source list.
    $rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @($build) "$logs\cmake-reconfigure-$stamp.log"
}
if ($rc) { throw "[RECOMP] CMake reconfigure failed ($rc): $logs\cmake-reconfigure-$stamp.log" }
$start = Get-Date
$rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @('--build', $build, '--target', 'chavokart', '--parallel', "$Jobs") "$logs\build-pc-$stamp.log"
Get-Content "$logs\build-pc-$stamp.log" -Tail 15
if ($rc) { throw "[RECOMP] PC build failed ($rc): $logs\build-pc-$stamp.log" }
Write-Output ("[RECOMP] PC build finished in {0:N1} min: {1}" -f ((Get-Date) - $start).TotalMinutes, (Join-Path $build 'chavokart.exe'))
