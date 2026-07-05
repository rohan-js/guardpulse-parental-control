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

data class ParentMode(
    val modeId: String,
    val name: String,
    val appPolicies: Map<String, ParentPolicy> = emptyMap(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class ActiveMode(
    val modeId: String? = null,
    val modeName: String? = null,
    val activatedAt: Long? = null
)

data class SafeModeState(
    val enabled: Boolean = false,
    val until: Long? = null,
    val startedAt: Long? = null,
    val startedBy: String? = null
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean {
        return enabled && (until ?: 0L) > now
    }
}

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
    val lastSyncError: String? = null,
    val safeModeActive: Boolean = false,
    val safeModeUntil: Long? = null,
    val activeModeId: String? = null,
    val activeModeName: String? = null
)

data class UnlockRequest(
    val requestId: String,
    val packageName: String,
    val reason: String,
    val status: String,
    val createdAt: Long?,
    val expiresAt: Long?,
    val updatedAt: Long? = null,
    val approvalType: String? = null,
    val approvalDurationMs: Long? = null
)

data class TamperEvent(
    val eventId: String,
    val type: String,
    val message: String?,
    val createdAt: Long?
)
