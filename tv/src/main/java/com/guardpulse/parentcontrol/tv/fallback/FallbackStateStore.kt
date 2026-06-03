package com.guardpulse.parentcontrol.tv.fallback

import android.content.Context
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

    fun shouldReportTamper(type: String): Boolean {
        val key = "tamper:$type"
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < PolicyConstants.TAMPER_EVENT_THROTTLE_MS) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }
}
