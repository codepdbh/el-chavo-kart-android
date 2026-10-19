# Generates C++ from default.xex with ReXGlue (out/pc build tree, target chavokart_codegen).
#
#   powershell -ExecutionPolicy Bypass -File scripts\generate-code.ps1 [-Force]
#
# Logs: out\logs\codegen-<timestamp>.log (stdout of rexglue) and out\logs\cmake-*.log.
# Exit code 0 only when code generation finished without analysis errors.
param([switch]$Force, [string]$LlvmBin, [string]$CMakeBin, [int]$Jobs = 8)
. "$PSScriptRoot\common.ps1"
Assert-CkXex (Join-Path $ProjectRoot 'game_xex\default.xex')
Enter-CkHostToolchain $LlvmBin $CMakeBin
$build = Join-Path $ProjectRoot 'out\pc'
$logs = Join-Path $ProjectRoot 'out\logs'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$root = $ProjectRoot.Replace('\', '/')
$rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @('-S', $root, '-B', $build.Replace('\', '/'), '-G', 'Ninja',
    "-DCMAKE_MAKE_PROGRAM=$($script:CMakeBin.Replace('\','/'))/ninja.exe",
    "-DCMAKE_C_COMPILER=$($script:LlvmBin.Replace('\','/'))/clang.exe",
    "-DCMAKE_CXX_COMPILER=$($script:LlvmBin.Replace('\','/'))/clang++.exe", '-DCMAKE_BUILD_TYPE=Release',
    '-DCMAKE_C_FLAGS=-march=x86-64-v2', '-DCMAKE_CXX_FLAGS=-march=x86-64-v2') "$logs\cmake-configure-$stamp.log"
if ($rc) { throw "[RECOMP] CMake configure failed ($rc): $logs\cmake-configure-$stamp.log" }
# Build the code generator first, then run it directly so its full report lands in one log.
$rc = Invoke-CkLogged "$script:CMakeBin\cmake.exe" @('--build', $build, '--target', 'rexglue', '--parallel', "$Jobs") "$logs\cmake-rexglue-$stamp.log"
if ($rc) { throw "[RECOMP] rexglue build failed ($rc): $logs\cmake-rexglue-$stamp.log" }
$rexglue = Join-Path $build 'sdk-bin\rexglue.exe'
$cgArgs = @('--log-file', "$logs\codegen-$stamp.detail.log", 'codegen', "$root/chavokart_manifest.toml")
if ($Force) { $cgArgs += '--ignore-stamp' }
Push-Location $ProjectRoot
try { $rc = Invoke-CkLogged $rexglue $cgArgs "$logs\codegen-$stamp.log" } finally { Pop-Location }
Get-Content "$logs\codegen-$stamp.log" -Tail 30
if ($rc) { throw "[RECOMP] Code generation failed ($rc): $logs\codegen-$stamp.log" }
# Same-function branch placeholders -> original local gotos (see tools/fix_local_branches.py).
& python (Join-Path $ProjectRoot 'tools\fix_local_branches.py')
if ($LASTEXITCODE -ne 0) { throw '[RECOMP] Unresolved REX_FATAL placeholders remain (analysis/functions/unresolved_functions.json)' }
$image = Join-Path $ProjectRoot 'out\analysis\chavokart-82000000.bin'
if (-not (Test-Path -LiteralPath $image)) {
    & python (Join-Path $ProjectRoot 'tools\xex_analyze.py') --xex (Join-Path $ProjectRoot 'game_xex\default.xex') `
        --sdk (Join-Path $ProjectRoot 'references\nfsmw-android\sdk') --out (Join-Path $ProjectRoot 'analysis') --image $image
    if ($LASTEXITCODE -ne 0) { throw '[XEX] analysis failed' }
}
& python (Join-Path $ProjectRoot 'tools\function_report.py') --generated (Join-Path $ProjectRoot 'generated\default') `
    --xex-report (Join-Path $ProjectRoot 'analysis\xex\xex_report.json') --image $image --out (Join-Path $ProjectRoot 'analysis\functions')
if ($LASTEXITCODE -ne 0) { throw '[RECOMP] function report failed' }
Write-Output "[RECOMP] Generated sources: $ProjectRoot\generated\default"
