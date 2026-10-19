# One-time setup: pinned reference checkouts, SDK third-party sources and the game_xex junction.
#
#   powershell -ExecutionPolicy Bypass -File scripts\setup.ps1 [-GameDir <folder with default.xex>]
#
# Without -GameDir it searches the project's parent folder (depth 3) for default.xex and accepts only the
# supported SHA-256. The game copy is only linked (junction), never copied or modified. Safe to run repeatedly.
param([string]$GameDir)
. "$PSScriptRoot\common.ps1"
$refs = Join-Path $ProjectRoot 'references'
New-Item -ItemType Directory -Force $refs | Out-Null
$pins = @(
    @{ Name = 'nfsmw-android'; Url = 'https://github.com/codepdbh/nfsmw-android.git'; Commit = '507a63f7d21c9b8e2d6f15d3ed0e12b9b2c38712' },
    @{ Name = 'nfs-carbon'; Url = 'https://github.com/codepdbh/NFS-CARBON-360-ANDROID-RECOMP.git'; Commit = 'c3ee962bc6d9f20650d0225309b64d7297bca1f4' }
)
foreach ($pin in $pins) {
    $dir = Join-Path $refs $pin.Name
    if (-not (Test-Path (Join-Path $dir '.git'))) {
        $rc = Invoke-CkLogged 'git' @('clone', '--quiet', $pin.Url, $dir) (Join-Path $ProjectRoot "out\logs\clone-$($pin.Name).log")
        if ($rc) { throw "[SETUP] git clone failed for $($pin.Url)" }
    }
    $head = (& git -C $dir rev-parse HEAD).Trim()
    if ($head -ne $pin.Commit) {
        $rc = Invoke-CkLogged 'git' @('-C', $dir, 'checkout', '--quiet', $pin.Commit) (Join-Path $ProjectRoot "out\logs\checkout-$($pin.Name).log")
        if ($rc) { throw "[SETUP] $($pin.Name) cannot be moved to the pinned commit $($pin.Commit) (local changes?)" }
    }
    Write-Output "[SETUP] $($pin.Name) @ $($pin.Commit)"
}
# SDK third-party sources (rexglue-sdk v0.10.0 + submodules); the fork's script keeps its patched files.
if (-not (Test-Path (Join-Path $refs 'nfsmw-android\sdk\thirdparty\fmt\CMakeLists.txt'))) {
    & python (Join-Path $refs 'nfsmw-android\tools\fetch_thirdparty.py')
    if ($LASTEXITCODE -ne 0) { throw '[SETUP] SDK third-party fetch failed' }
}
# Game junction
$link = Join-Path $ProjectRoot 'game_xex'
if (-not (Test-Path $link)) {
    if (-not $GameDir) {
        $parent = Split-Path $ProjectRoot -Parent
        foreach ($xex in Get-ChildItem -Path $parent -Filter 'default.xex' -Recurse -Depth 3 -ErrorAction SilentlyContinue) {
            $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $xex.FullName).Hash.ToLowerInvariant()
            if ($script:SupportedXex.ContainsKey($hash)) { $GameDir = $xex.DirectoryName; break }
            Write-Output "[SETUP] ignored $($xex.FullName) (SHA-256 $hash is not the supported revision)"
        }
    }
    if (-not $GameDir) { throw '[SETUP] No supported default.xex found; pass -GameDir' }
    New-Item -ItemType Junction -Path $link -Target $GameDir | Out-Null
    Write-Output "[SETUP] game_xex -> $GameDir"
}
Assert-CkXex (Join-Path $link 'default.xex')
