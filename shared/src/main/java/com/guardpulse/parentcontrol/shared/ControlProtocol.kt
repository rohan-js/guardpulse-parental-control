package com.guardpulse.parentcontrol.shared

import com.google.firebase.database.DataSnapshot

data class ControlAppRule(
    val packageName: String,
    val manualBlocked: Boolean = false,
    val dailyLimitMinutes: Int? = null,
    val updatedAt: Long? = null
)

data class ControlMode(
    val modeId: String,
    val name: String,
    val apps: Map<String, ControlAppRule> = emptyMap(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class ControlActiveMode(
    val modeId: String,
    val modeName: String? = null,
    val activatedAt: Long? = null
)

data class ControlSafeMode(
    val enabled: Boolean = false,
    val until: Long = 0L,
    val startedAt: Long? = null,
    val startedBy: String? = null
)

data class ControlPin(
    val salt: String,
    val hash: String,
    val updatedAt: Long? = null
)

data class ControlSnapshotV2(
    val revisionId: String,
    val updatedAt: Long? = null,
    val updatedBy: String? = null,
    val apps: Map<String, ControlAppRule> = emptyMap(),
    val modes: Map<String, ControlMode> = emptyMap(),
    val activeMode: ControlActiveMode? = null,
    val safeMode: ControlSafeMode = ControlSafeMode(),
    val pin: ControlPin? = null
) {
    fun effectiveApps(): Map<String, ControlAppRule> {
        val selectedMode = activeMode?.let { modes[it.modeId] }
        val result = (selectedMode?.apps ?: apps).toMutableMap()
        PolicyConstants.defaultLockedPackages.forEach { packageName ->
            result.putIfAbsent(packageName, ControlAppRule(packageName, manualBlocked = true))
        }
        return result
    }

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "schemaVersion" to PolicyConstants.SYNC_PROTOCOL_VERSION,
        "revisionId" to revisionId,
        "updatedAt" to updatedAt,
        "updatedBy" to updatedBy,
        "apps" to apps.mapKeys { PackageKeys.encode(it.key) }.mapValues { it.value.toFirebaseMap() },
        "modes" to modes.mapValues { it.value.toFirebaseMap() },
        "activeMode" to activeMode?.toFirebaseMap(),
        "safeMode" to safeMode.toFirebaseMap(),
        "pin" to pin?.toFirebaseMap()
    )
}

data class SyncDesiredRevision(
    val revisionId: String,
    val kind: String,
    val target: String? = null,
    val requestedAt: Long? = null,
    val requestedBy: String? = null
)

data class SyncAppliedRevision(
    val revisionId: String? = null,
    val status: String? = null,
    val appliedAt: Long? = null,
    val sessionId: String? = null,
    val error: String? = null
)

data class SyncRuntimeState(
    val connected: Boolean = false,
    val sessionId: String? = null,
    val protocolVersion: Int = 0,
    val connectedAt: Long? = null,
    val lastPolicyReceivedAt: Long? = null,
    val lastPolicyAppliedAt: Long? = null,
    val lastStateWriteAt: Long? = null,
    val lastUsageWriteAt: Long? = null,
    val lastHeartbeatWriteAt: Long? = null,
    val lastInventoryWriteAt: Long? = null,
    val lastHealthWriteAt: Long? = null,
    val lastCommandWriteAt: Long? = null,
    val lastUnlockWriteAt: Long? = null,
    val lastTamperWriteAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastFailedChannel: String? = null,
    val lastError: String? = null,
    val lastErrorAt: Long? = null,
    val inventoryRevision: String? = null
)

enum class DeviceFreshness { LIVE, DELAYED, OFFLINE }

object ControlProtocol {
    private val revisionKinds = setOf(
        PolicyConstants.REVISION_APP_POLICY,
        PolicyConstants.REVISION_MODE_CREATE,
        PolicyConstants.REVISION_MODE_UPDATE,
        PolicyConstants.REVISION_MODE_DELETE,
        PolicyConstants.REVISION_MODE_POLICY,
        PolicyConstants.REVISION_ACTIVE_MODE,
        PolicyConstants.REVISION_SAFE_MODE,
        PolicyConstants.REVISION_PIN,
        PolicyConstants.REVISION_MIGRATION
    )

    fun parse(snapshot: DataSnapshot): Result<ControlSnapshotV2> = runCatching {
        require(snapshot.exists()) { "V2 control snapshot is missing" }
        val schemaVersion = snapshot.child("schemaVersion").getValue(Long::class.java)?.toInt()
        require(schemaVersion == PolicyConstants.SYNC_PROTOCOL_VERSION) {
            "Unsupported control schema: ${schemaVersion ?: "missing"}"
        }
        val revisionId = snapshot.child("revisionId").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: error("Control revision is missing")
        val apps = parseApps(snapshot.child("apps"))
        val modes = snapshot.child("modes").children.associate { modeSnapshot ->
            val modeId = modeSnapshot.child("modeId").getValue(String::class.java)
                ?: modeSnapshot.key
                ?: error("Mode ID is missing")
            require(modeId.isNotBlank()) { "Mode ID is blank" }
            val name = modeSnapshot.child("name").getValue(String::class.java)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: error("Mode $modeId has no name")
            modeId to ControlMode(
                modeId = modeId,
                name = name,
                apps = parseApps(modeSnapshot.child("apps")),
                createdAt = modeSnapshot.child("createdAt").getValue(Long::class.java),
                updatedAt = modeSnapshot.child("updatedAt").getValue(Long::class.java)
            )
        }
        val activeModeSnapshot = snapshot.child("activeMode")
        val activeMode = if (activeModeSnapshot.exists()) {
            val modeId = activeModeSnapshot.child("modeId").getValue(String::class.java)
                ?.takeIf { it.isNotBlank() }
                ?: error("Active mode ID is missing")
            require(modes.containsKey(modeId)) { "Active mode $modeId does not exist" }
            ControlActiveMode(
                modeId = modeId,
                modeName = activeModeSnapshot.child("modeName").getValue(String::class.java),
                activatedAt = activeModeSnapshot.child("activatedAt").getValue(Long::class.java)
            )
        } else {
            null
        }
        val safeModeSnapshot = snapshot.child("safeMode")
        val safeMode = ControlSafeMode(
            enabled = safeModeSnapshot.child("enabled").getValue(Boolean::class.java) ?: false,
            until = safeModeSnapshot.child("until").getValue(Long::class.java) ?: 0L,
            startedAt = safeModeSnapshot.child("startedAt").getValue(Long::class.java),
            startedBy = safeModeSnapshot.child("startedBy").getValue(String::class.java)
        )
        require(!safeMode.enabled || safeMode.until > 0L) { "Safe Mode expiry is invalid" }
        val pinSnapshot = snapshot.child("pin")
        val pin = if (pinSnapshot.exists()) {
            val salt = pinSnapshot.child("salt").getValue(String::class.java)
                ?.takeIf { it.isNotBlank() }
                ?: error("PIN salt is missing")
            val hash = pinSnapshot.child("hash").getValue(String::class.java)
                ?.takeIf { it.isNotBlank() }
                ?: error("PIN hash is missing")
            ControlPin(salt, hash, pinSnapshot.child("updatedAt").getValue(Long::class.java))
        } else {
            null
        }
        ControlSnapshotV2(
            revisionId = revisionId,
            updatedAt = snapshot.child("updatedAt").getValue(Long::class.java),
            updatedBy = snapshot.child("updatedBy").getValue(String::class.java),
            apps = apps,
            modes = modes,
            activeMode = activeMode,
            safeMode = safeMode,
            pin = pin
        )
    }

    fun parseDesired(snapshot: DataSnapshot): SyncDesiredRevision? {
        val revisionId = snapshot.child("revisionId").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val kind = snapshot.child("kind").getValue(String::class.java)
            ?.takeIf { it in revisionKinds }
            ?: return null
        return SyncDesiredRevision(
            revisionId = revisionId,
            kind = kind,
            target = snapshot.child("target").getValue(String::class.java),
            requestedAt = snapshot.child("requestedAt").getValue(Long::class.java),
            requestedBy = snapshot.child("requestedBy").getValue(String::class.java)
        )
    }

    fun freshness(connected: Boolean, lastSeen: Long?, now: Long): DeviceFreshness {
        val age = lastSeen?.let { (now - it).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        return when {
            !connected || age > 90_000L -> DeviceFreshness.OFFLINE
            age > 45_000L -> DeviceFreshness.DELAYED
            else -> DeviceFreshness.LIVE
        }
    }

    private fun parseApps(snapshot: DataSnapshot): Map<String, ControlAppRule> {
        return snapshot.children.associate { appSnapshot ->
            val packageName = appSnapshot.child("packageName").getValue(String::class.java)
                ?: runCatching { PackageKeys.decode(appSnapshot.key.orEmpty()) }.getOrNull()
                ?: error("App package is missing")
            val limit = appSnapshot.child("dailyLimitMinutes").getValue(Long::class.java)
                ?.toInt()
                ?.takeIf { it in 1..1440 }
            packageName to ControlAppRule(
                packageName = packageName,
                manualBlocked = appSnapshot.child("manualBlocked").getValue(Boolean::class.java) ?: false,
                dailyLimitMinutes = limit,
                updatedAt = appSnapshot.child("updatedAt").getValue(Long::class.java)
            )
        }
    }
}

private fun ControlAppRule.toFirebaseMap(): Map<String, Any?> = mapOf(
    "packageName" to packageName,
    "manualBlocked" to manualBlocked,
    "dailyLimitMinutes" to dailyLimitMinutes,
    "updatedAt" to updatedAt
)

private fun ControlMode.toFirebaseMap(): Map<String, Any?> = mapOf(
    "modeId" to modeId,
    "name" to name,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "apps" to apps.mapKeys { PackageKeys.encode(it.key) }.mapValues { it.value.toFirebaseMap() }
)

private fun ControlActiveMode.toFirebaseMap(): Map<String, Any?> = mapOf(
    "modeId" to modeId,
    "modeName" to modeName,
    "activatedAt" to activatedAt
)

private fun ControlSafeMode.toFirebaseMap(): Map<String, Any?> = mapOf(
    "enabled" to enabled,
    "until" to until,
    "startedAt" to startedAt,
    "startedBy" to startedBy
)

private fun ControlPin.toFirebaseMap(): Map<String, Any?> = mapOf(
    "salt" to salt,
    "hash" to hash,
    "updatedAt" to updatedAt
)
