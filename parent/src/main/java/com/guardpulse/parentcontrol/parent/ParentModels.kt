package com.guardpulse.parentcontrol.parent

import com.guardpulse.parentcontrol.shared.PolicyConstants

data class ParentDevice(
    val deviceId: String,
    val label: String,
    val lastSeen: Long?,
    val online: Boolean = false,
    val enforcementMode: String = PolicyConstants.ENFORCEMENT_UNPROTECTED,
    val protectionHealthy: Boolean = false
)

data class ParentApp(
    val packageName: String,
    val label: String,
    val blockable: Boolean,
    val protectedReason: String?
)

data class ParentPolicy(
    val manualBlocked: Boolean = false,
    val dailyLimitMinutes: Int? = null
)

data class ParentState(
    val suspended: Boolean = false,
    val requestedSuspended: Boolean = false,
    val manualBlocked: Boolean = false,
    val dailyLimitBlocked: Boolean = false,
    val networkBlocked: Boolean = false,
    val vpnApplied: Boolean = false,
    val vpnActive: Boolean = false,
    val lockBlocked: Boolean = false,
    val lockReason: String? = null,
    val vpnLastError: String? = null,
    val fallbackLocked: Boolean = false,
    val enforcementMode: String = PolicyConstants.ENFORCEMENT_UNPROTECTED,
    val blockReason: String? = null,
    val usageMinutesToday: Long = 0,
    val lastError: String? = null
)

data class SecurityRuntime(
    val enforcementMode: String = PolicyConstants.ENFORCEMENT_UNPROTECTED,
    val deviceOwner: Boolean = false,
    val deviceAdmin: Boolean = false,
    val deviceAdminSetupAvailable: Boolean = true,
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val vpnPrepared: Boolean = false,
    val vpnActive: Boolean = false,
    val vpnBlockedCount: Int = 0,
    val vpnLastError: String? = null,
    val backgroundUnrestricted: Boolean = false,
    val pinConfigured: Boolean = false,
    val protectionHealthy: Boolean = false,
    val lastForegroundPackage: String? = null,
    val lastSyncError: String? = null
)

data class UnlockRequest(
    val requestId: String,
    val packageName: String,
    val reason: String,
    val status: String,
    val createdAt: Long?,
    val expiresAt: Long?
)

data class TamperEvent(
    val eventId: String,
    val type: String,
    val message: String?,
    val createdAt: Long?
)
