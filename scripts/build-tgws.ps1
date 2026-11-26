# Builds the TG WS Proxy Go runtime (libtgwsproxy.so) for Android.
# Requires Go 1.25+ and the Android NDK. Output: app/src/main/jniLibs/<abi>/.
# NOTE: keep this file ASCII-only: Windows PowerShell 5.1 reads scripts as ANSI.
[CmdletBinding()]
param(
    [int]$ApiLevel = 26,
    [ValidateSet('armeabi-v7a', 'arm64-v8a')]
    [string[]]$Abis = @('arm64-v8a', 'armeabi-v7a'),
    [string]$GoExe = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$moduleDir = Join-Path $repoRoot 'third_party\tgwsproxy\native\tgwsproxy'
$jniLibs = Join-Path $repoRoot 'app\src\main\jniLibs'

if (-not (Test-Path -LiteralPath (Join-Path $moduleDir 'go.mod'))) {
    throw "Go module not found: $moduleDir. Run: git submodule update --init --recursive"
}

if (-not $GoExe) {
    $cmd = Get-Command go -ErrorAction SilentlyContinue
    if ($cmd) { $GoExe = $cmd.Source }
    elseif (Test-Path 'C:\Dev\go\bin\go.exe') { $GoExe = 'C:\Dev\go\bin\go.exe' }
    elseif (Test-Path 'C:\Program Files\Go\bin\go.exe') { $GoExe = 'C:\Program Files\Go\bin\go.exe' }
    else { throw 'Go not found. Install Go 1.25+ or pass -GoExe.' }
}

$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = 'C:\Dev\sdk' }
$ndk = $env:ANDROID_NDK_ROOT
if (-not $ndk) {
    $ndkBase = Join-Path $sdk 'ndk'
    if (-not (Test-Path -LiteralPath $ndkBase)) { throw "NDK not found: $ndkBase" }
    $ndk = (Get-ChildItem $ndkBase -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
}
$toolchain = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$readelf = Join-Path $toolchain 'llvm-readelf.exe'

$targets = @{
    'armeabi-v7a' = @{ GoArch = 'arm'; GoArm = '7'; Triple = 'armv7a-linux-androideabi'; ElfClass = 'ELF32'; Machine = '(ARM|EM_ARM)' }
    'arm64-v8a'   = @{ GoArch = 'arm64'; GoArm = $null; Triple = 'aarch64-linux-android'; ElfClass = 'ELF64'; Machine = '(AArch64|EM_AARCH64)' }
}

Write-Host "Go: $GoExe"
& $GoExe version
Write-Host "NDK: $ndk"

$saved = @{}
foreach ($name in @('GOOS', 'GOARCH', 'GOARM', 'CGO_ENABLED', 'CC', 'CGO_LDFLAGS')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    foreach ($abi in $Abis) {
        $target = $targets[$abi]
        $clang = Join-Path $toolchain "$($target.Triple)$ApiLevel-clang.cmd"
        if (-not (Test-Path -LiteralPath $clang)) { $clang = Join-Path $toolchain "$($target.Triple)$ApiLevel-clang.exe" }
        if (-not (Test-Path -LiteralPath $clang)) { throw "NDK compiler not found for $abi (API $ApiLevel)" }

        $outDir = Join-Path $jniLibs $abi
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        $outLib = Join-Path $outDir 'libtgwsproxy.so'
        if (Test-Path -LiteralPath $outLib) { Remove-Item -LiteralPath $outLib -Force }

        $env:GOOS = 'android'
        $env:GOARCH = $target.GoArch
        $env:CGO_ENABLED = '1'
        $env:CC = $clang
        # Android 15+ devices with 16K pages require 16K-aligned ELF segments.
        $env:CGO_LDFLAGS = '-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'
        [Environment]::SetEnvironmentVariable('GOARM', $target.GoArm, 'Process')

        Write-Host "==> $abi (GOARCH=$($target.GoArch), API=$ApiLevel)"
        Push-Location $moduleDir
        try {
            & $GoExe build -buildmode=c-shared -trimpath -o $outLib .
            if ($LASTEXITCODE -ne 0) { throw "go build failed for $abi (exit $LASTEXITCODE)" }
        }
        finally {
            Pop-Location
        }
        Remove-Item -LiteralPath ([IO.Path]::ChangeExtension($outLib, '.h')) -Force -ErrorAction SilentlyContinue

        if (-not (Test-Path -LiteralPath $outLib)) { throw "Library was not produced for $abi" }
        if (Test-Path -LiteralPath $readelf) {
            $header = (& $readelf -h $outLib 2>&1) -join "`n"
            if ($header -notmatch "Class:\s+$($target.ElfClass)\b" -or $header -notmatch "Machine:\s+$($target.Machine)\b") {
                throw "Wrong ELF architecture for ${abi}:`n$header"
            }
            $segments = (& $readelf -l $outLib 2>&1) -join "`n"
            if ($segments -notmatch '0x4000') {
                throw "ELF segments are not 16K-aligned for $abi (Android 15+ requires it)"
            }
        }
        $size = [math]::Round((Get-Item $outLib).Length / 1MB, 2)
        Write-Host "OK $abi -> $outLib ($size MB, 16K-aligned)"
    }
}
finally {
    foreach ($name in $saved.Keys) {
        [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process')
    }
}
