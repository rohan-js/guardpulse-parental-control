package com.guardpulse.parentcontrol.shared

object FirebasePaths {
    fun userDevices(parentUid: String) = "users/$parentUid/devices"
    fun userDevice(parentUid: String, deviceId: String) = "users/$parentUid/devices/$deviceId"

    fun deviceRoot(deviceId: String) = "devices/$deviceId"
    fun deviceMeta(deviceId: String) = "devices/$deviceId/meta"
    fun deviceApps(deviceId: String) = "devices/$deviceId/apps"
    fun deviceApp(deviceId: String, packageName: String) =
        "devices/$deviceId/apps/${PackageKeys.encode(packageName)}"
    fun devicePolicyApps(deviceId: String) = "devices/$deviceId/policy/apps"
    fun devicePolicyApp(deviceId: String, packageName: String) =
        "devices/$deviceId/policy/apps/${PackageKeys.encode(packageName)}"
    fun devicePolicyModes(deviceId: String) = "devices/$deviceId/policy/modes"
    fun devicePolicyMode(deviceId: String, modeId: String) =
        "devices/$deviceId/policy/modes/$modeId"
    fun devicePolicyModeApp(deviceId: String, modeId: String, packageName: String) =
        "devices/$deviceId/policy/modes/$modeId/apps/${PackageKeys.encode(packageName)}"
    fun devicePolicyActiveMode(deviceId: String) = "devices/$deviceId/policy/activeMode"
    fun deviceStateApps(deviceId: String) = "devices/$deviceId/state/apps"
    fun deviceStateApp(deviceId: String, packageName: String) =
        "devices/$deviceId/state/apps/${PackageKeys.encode(packageName)}"
    fun deviceHeartbeat(deviceId: String) = "devices/$deviceId/heartbeat"
    fun deviceCommands(deviceId: String) = "devices/$deviceId/commands"
    fun deviceSecurity(deviceId: String) = "devices/$deviceId/security"
    fun deviceSecurityPin(deviceId: String) = "devices/$deviceId/security/pin"
    fun deviceSecurityRuntime(deviceId: String) = "devices/$deviceId/security/runtime"
    fun deviceSecuritySafeMode(deviceId: String) = "devices/$deviceId/security/safeMode"
    fun deviceTamperEvents(deviceId: String) = "devices/$deviceId/tamperEvents"
    fun deviceUnlockRequests(deviceId: String) = "devices/$deviceId/unlockRequests"
    fun deviceUnlockRequest(deviceId: String, requestId: String) =
        "devices/$deviceId/unlockRequests/$requestId"
    fun pairRequests(deviceId: String) = "pairRequests/$deviceId"
}
