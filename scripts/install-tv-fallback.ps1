param(
    [string]$ApkPath = "",
    [string]$AdbPath = "",
    [string]$Serial = ""
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

$adbArgs = @()
if (![string]::IsNullOrWhiteSpace($Serial)) {
    $adbArgs = @("-s", $Serial)
}

& $AdbPath devices
& $AdbPath @adbArgs install -r $ApkPath
if ($LASTEXITCODE -ne 0) {
    throw "APK install failed."
}

& $AdbPath @adbArgs shell appops set com.guardpulse.parentcontrol.tv GET_USAGE_STATS allow
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Usage access could not be granted by ADB. Open Usage Access from the TV app and enable it manually."
}

$component = "com.guardpulse.parentcontrol.tv/.admin.TvDeviceAdminReceiver"
& $AdbPath @adbArgs shell dpm set-active-admin $component
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Device Admin could not be activated by ADB. This is expected on some Xiaomi TV firmware; fallback protection can still run with Accessibility + Usage Access + PIN."
}

$service = "com.guardpulse.parentcontrol.tv/com.guardpulse.parentcontrol.tv.fallback.AppMonitorAccessibilityService"
$current = (& $AdbPath @adbArgs shell settings get secure enabled_accessibility_services).Trim()
if ($current -eq "null") {
    $current = ""
}

$services = @()
if (![string]::IsNullOrWhiteSpace($current)) {
    $services = $current -split ":" | Where-Object { ![string]::IsNullOrWhiteSpace($_) }
}

if ($services -notcontains $service) {
    $services += $service
}

& $AdbPath @adbArgs shell settings put secure enabled_accessibility_services ($services -join ":")
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Accessibility service could not be enabled by ADB. Open Accessibility Settings from the TV app."
}

& $AdbPath @adbArgs shell settings put secure accessibility_enabled 1
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Accessibility could not be switched on by ADB. Open Accessibility Settings from the TV app."
}

& $AdbPath @adbArgs shell dumpsys deviceidle whitelist +com.guardpulse.parentcontrol.tv | Out-Null

& $AdbPath @adbArgs shell monkey -p com.guardpulse.parentcontrol.tv 1
if ($LASTEXITCODE -ne 0) {
    throw "Launching TV app failed."
}

Write-Host "TV fallback app installed. Confirm Accessibility, Usage Access, Firebase sync, and Parent PIN in the TV app. Device Admin is optional and may be unavailable on Xiaomi firmware."
