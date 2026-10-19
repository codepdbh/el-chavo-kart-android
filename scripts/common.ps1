# Shared helpers for the El Chavo Kart scripts. Dot-source it: . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
# Only executables listed here may be used with the generated code in generated/default.
$script:SupportedXex = @{
    '1db4e96b413d5629206ffe63e73c0e1cfaca9eea24bcf6fced6db9b135ce120f' = 'El Chavo Kart (Xbox 360) title 475807D7 media 5C2ECDD6 v0.0.0.3'
}

function Get-CkTool([string]$Override, [string]$Default, [string]$Name) {
    $path = if ($Override) { $Override } else { $Default }
    if (-not (Test-Path -LiteralPath $path)) { throw "[$Name] not found: $path" }
    return $path
}

function Enter-CkHostToolchain([string]$LlvmBin, [string]$CMakeBin) {
    $script:LlvmBin = Get-CkTool $LlvmBin 'C:\Program Files\LLVM\bin' 'LLVM'
    $script:CMakeBin = Get-CkTool $CMakeBin (Join-Path $env:LOCALAPPDATA 'Android\Sdk\cmake\3.30.5\bin') 'CMake'
    foreach ($exe in @("$script:LlvmBin\clang++.exe", "$script:CMakeBin\cmake.exe", "$script:CMakeBin\ninja.exe")) {
        if (-not (Test-Path -LiteralPath $exe)) { throw "Missing tool: $exe" }
    }
    $vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    $vsRoot = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if (-not $vsRoot) { throw 'Visual Studio C++ build tools (x64) not found' }
    Import-Module (Join-Path $vsRoot 'Common7\Tools\Microsoft.VisualStudio.DevShell.dll')
    Enter-VsDevShell -VsInstallPath $vsRoot -SkipAutomaticLocation -DevCmdArguments '-arch=x64 -host_arch=x64' | Out-Null
    $env:PATH = "$script:LlvmBin;$script:CMakeBin;$env:PATH"
}

# Runs a native tool with stdout/stderr in log files (Windows PowerShell 5.1 turns stderr lines into errors).
function Invoke-CkLogged([string]$Exe, [string[]]$Arguments, [string]$Log) {
    New-Item -ItemType Directory -Force (Split-Path $Log) | Out-Null
    $quoted = $Arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
    $p = Start-Process -FilePath $Exe -ArgumentList $quoted -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $Log -RedirectStandardError "$Log.err"
    return $p.ExitCode
}

function Assert-CkXex([string]$XexPath) {
    if (-not (Test-Path -LiteralPath $XexPath)) { throw "[XEX] default.xex not found: $XexPath (run scripts\setup.ps1)" }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $XexPath).Hash.ToLowerInvariant()
    if (-not $script:SupportedXex.ContainsKey($hash)) {
        throw "[XEX] Unsupported executable revision (SHA-256 $hash). Generated code must not be reused for another revision."
    }
    Write-Output "[XEX] $($script:SupportedXex[$hash])"
}
