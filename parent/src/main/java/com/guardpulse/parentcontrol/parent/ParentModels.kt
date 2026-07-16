package com.guardpulse.parentcontrol.parent

import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.guardpulse.parentcontrol.shared.SyncAppliedRevision
import com.guardpulse.parentcontrol.shared.SyncDesiredRevision
import com.guardpulse.parentcontrol.shared.SyncRuntimeState

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
    val usageMsToday: Long = 0,
    val usageCapturedAt: Long? = null,
    val foregroundActive: Boolean = false,
    val foregroundStartedAt: Long? = null,
    val updatedAt: Long? = null,
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
    val activeModeName: String? = null,
    val updatedAt: Long? = null
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
    val approvalDurationMs: Long? = null,
    val tvApplyStatus: String? = null,
    val tvAppliedAt: Long? = null
)

data class TamperEvent(
    val eventId: String,
    val type: String,
    val message: String?,
    val createdAt: Long?
)

data class ParentCommand(
    val commandId: String,
    val type: String,
    val packageName: String? = null,
    val status: String = PolicyConstants.COMMAND_PENDING,
    val createdAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val error: String? = null
)

data class PairRequestState(
    val deviceId: String,
    val requestId: String,
    val status: String = PolicyConstants.COMMAND_PENDING,
    val createdAt: Long? = null,
    val expiresAt: Long? = null,
    val error: String? = null
)

enum class ParentSyncStatus {
    IDLE,
    SENDING,
    WAITING_FOR_TV,
    APPLIED,
    DELAYED,
    OFFLINE_PENDING,
    FAILED,
    TV_UPDATE_REQUIRED
}

data class ParentSyncUiState(
    val configured: Boolean = true,
    val firebaseMessage: String? = null,
    val signedIn: Boolean = false,
    val authBusy: Boolean = false,
    val phoneConnected: Boolean = false,
    val serverNow: Long = System.currentTimeMillis(),
    val devices: List<ParentDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val apps: Map<String, ParentApp> = emptyMap(),
    val policies: Map<String, ParentPolicy> = emptyMap(),
    val states: Map<String, ParentState> = emptyMap(),
    val modes: List<ParentMode> = emptyList(),
    val activeMode: ActiveMode = ActiveMode(),
    val safeMode: SafeModeState = SafeModeState(),
    val security: SecurityRuntime = SecurityRuntime(),
    val unlockRequests: List<UnlockRequest> = emptyList(),
    val tamperEvents: List<TamperEvent> = emptyList(),
    val commands: List<ParentCommand> = emptyList(),
    val pairRequest: PairRequestState? = null,
    val desiredRevision: SyncDesiredRevision? = null,
    val appliedRevision: SyncAppliedRevision = SyncAppliedRevision(),
    val syncRuntime: SyncRuntimeState = SyncRuntimeState(),
    val controlV2Exists: Boolean = false,
    val loadingDevices: Boolean = false,
    val loadingDeviceDetails: Boolean = false,
    val message: String? = null
)
