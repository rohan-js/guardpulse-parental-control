param(
    [string]$ApkPath = "",
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $repo "tv\build\outputs\apk\ownerTest\tv-ownerTest.apk"
}

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}

if (!(Test-Path $ApkPath)) {
    throw "Owner-test TV APK not found: $ApkPath. Run .\gradlew.bat :tv:assembleOwnerTest first."
}

if (!(Test-Path $AdbPath)) {
    throw "adb.exe not found: $AdbPath"
}

& $AdbPath devices
& $AdbPath install -t -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    throw "Owner-test APK install failed."
}

Write-Host "Owner-test TV APK installed. This build is only for local Device Owner provisioning experiments."
