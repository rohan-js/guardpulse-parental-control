param(
    [string]$ApkPath = "",
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $repo "tv\build\outputs\apk\debug\tv-debug.apk"
}

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $AdbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}

if (!(Test-Path $ApkPath)) {
    throw "TV APK not found: $ApkPath. Run scripts\build.ps1 first."
}

if (!(Test-Path $AdbPath)) {
    throw "adb.exe not found: $AdbPath"
}

$component = "com.guardpulse.parentcontrol.tv/.admin.TvDeviceAdminReceiver"

& $AdbPath devices
& $AdbPath install -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed."
}

& $AdbPath shell dpm set-device-owner $component
if ($LASTEXITCODE -ne 0) {
    throw "Device Owner setup failed. Factory reset the TV, skip account sign-in, enable ADB/MiTV debugging, then run this script before adding Google/Xiaomi accounts."
}

& $AdbPath shell appops set com.guardpulse.parentcontrol.tv GET_USAGE_STATS allow
if ($LASTEXITCODE -ne 0) {
    throw "Granting usage access failed."
}

& $AdbPath shell monkey -p com.guardpulse.parentcontrol.tv 1
if ($LASTEXITCODE -ne 0) {
    throw "Launching TV app failed."
}

Write-Host "Provisioning completed. The TV app will apply Device Owner hardening policies on launch."
