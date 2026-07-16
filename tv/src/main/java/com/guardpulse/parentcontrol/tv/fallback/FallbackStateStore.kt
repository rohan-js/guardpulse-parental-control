package com.guardpulse.parentcontrol.tv.fallback

import android.content.Context
import com.guardpulse.parentcontrol.shared.DateKeys
import com.guardpulse.parentcontrol.shared.PolicyConstants
import org.json.JSONObject

data class PinRecord(
    val salt: String,
    val hash: String,
    val updatedAt: Long = 0L
)

data class FallbackDecision(
    val locked: Boolean,
    val reason: String? = null,
    val policyPackage: String? = null,
    val settingsSectionKey: String? = null
)

data class LiveForegroundSession(
    val packageName: String,
    val startedAt: Long,
    val baselineUsageMs: Long,
    val dayKey: String
)

class FallbackStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("fallback_state", Context.MODE_PRIVATE)

    fun savePin(pin: PinRecord?) {
        if (pin == null) {
            prefs.edit().remove("pin").apply()
            return
        }
        val json = JSONObject()
            .put("salt", pin.salt)
            .put("hash", pin.hash)
            .put("updatedAt", pin.updatedAt)
        prefs.edit().putString("pin", json.toString()).apply()
    }

    fun loadPin(): PinRecord? {
        val raw = prefs.getString("pin", null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val salt = json.optString("salt")
        val hash = json.optString("hash")
        if (salt.isBlank() || hash.isBlank()) return null
        return PinRecord(salt, hash, json.optLong("updatedAt", 0L))
    }

    fun grantTemporaryUnlock(packageName: String, durationMs: Long = PolicyConstants.TEMP_UNLOCK_MS) {
        val until = System.currentTimeMillis() + durationMs
        prefs.edit().putLong("unlock:$packageName", until).apply()
    }

    fun isTemporarilyUnlocked(packageName: String): Boolean {
        val until = prefs.getLong("unlock:$packageName", 0L)
        return until > System.currentTimeMillis()
    }

    fun grantAppVisitUnlock(policyPackage: String) {
        prefs.edit().putString("appVisitUnlock", policyPackage).apply()
    }

    fun isAppVisitUnlocked(policyPackage: String): Boolean {
        return prefs.getString("appVisitUnlock", null) == policyPackage
    }

    fun appVisitUnlockPackage(): String? = prefs.getString("appVisitUnlock", null)

    fun clearAppVisitUnlock() {
        prefs.edit().remove("appVisitUnlock").apply()
    }

    fun grantSettingsSectionUnlock(sectionKey: String) {
        prefs.edit().putString("settingsSectionUnlock", sectionKey).apply()
    }

    fun isSettingsSectionUnlocked(sectionKey: String): Boolean {
        return prefs.getString("settingsSectionUnlock", null) == sectionKey
    }

    fun clearSettingsSectionUnlock() {
        prefs.edit().remove("settingsSectionUnlock").apply()
    }

    fun grantSetupVisitUnlock() {
        prefs.edit().putBoolean("setupVisitUnlock", true).apply()
    }

    fun isSetupVisitUnlocked(): Boolean {
        return prefs.getBoolean("setupVisitUnlock", false)
    }

    fun clearSetupVisitUnlock() {
        prefs.edit().remove("setupVisitUnlock").apply()
    }

    fun grantSetupSettingsAccess(durationMs: Long = 120_000L) {
        prefs.edit().putLong("setupSettingsUntil", System.currentTimeMillis() + durationMs).apply()
    }

    fun isSetupSettingsAccessAllowed(): Boolean {
        return prefs.getLong("setupSettingsUntil", 0L) > System.currentTimeMillis()
    }

    fun saveLastForeground(packageName: String) {
        prefs.edit().putString("lastForeground", packageName).apply()
    }

    fun lastForeground(): String? = prefs.getString("lastForeground", null)

    fun startLiveForegroundSession(
        packageName: String,
        baselineUsageMs: Long,
        startedAt: Long = System.currentTimeMillis(),
        dayKey: String = DateKeys.today()
    ) {
        prefs.edit()
            .putString("liveForegroundPackage", packageName)
            .putLong("liveForegroundStartedAt", startedAt)
            .putLong("liveForegroundBaselineMs", baselineUsageMs.coerceAtLeast(0L))
            .putString("liveForegroundDay", dayKey)
            .apply()
    }

    fun liveForegroundSession(dayKey: String = DateKeys.today()): LiveForegroundSession? {
        val packageName = prefs.getString("liveForegroundPackage", null)?.takeIf { it.isNotBlank() }
            ?: return null
        val sessionDay = prefs.getString("liveForegroundDay", null) ?: return null
        if (sessionDay != dayKey) return null
        return LiveForegroundSession(
            packageName = packageName,
            startedAt = prefs.getLong("liveForegroundStartedAt", 0L),
            baselineUsageMs = prefs.getLong("liveForegroundBaselineMs", 0L),
            dayKey = sessionDay
        ).takeIf { it.startedAt > 0L }
    }

    fun clearLiveForegroundSession() {
        prefs.edit()
            .remove("liveForegroundPackage")
            .remove("liveForegroundStartedAt")
            .remove("liveForegroundBaselineMs")
            .remove("liveForegroundDay")
            .apply()
    }

    fun saveSafeMode(until: Long) {
        prefs.edit().putLong("safeModeUntil", until).apply()
    }

    fun safeModeUntil(): Long = prefs.getLong("safeModeUntil", 0L)

    fun saveServerTimeOffset(offsetMs: Long) {
        prefs.edit().putLong("serverTimeOffset", offsetMs).apply()
    }

    fun serverNow(): Long = System.currentTimeMillis() + prefs.getLong("serverTimeOffset", 0L)

    fun isSafeModeActive(): Boolean = safeModeUntil() > serverNow()

    fun shouldReportTamper(type: String): Boolean {
        val key = "tamper:$type"
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < PolicyConstants.TAMPER_EVENT_THROTTLE_MS) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }
}
