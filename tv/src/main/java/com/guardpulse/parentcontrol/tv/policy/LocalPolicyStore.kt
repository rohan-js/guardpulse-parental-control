package com.guardpulse.parentcontrol.tv.policy

import android.content.Context
import android.content.SharedPreferences
import com.guardpulse.parentcontrol.shared.DateKeys
import org.json.JSONObject

data class AppPolicy(
    val manualBlocked: Boolean = false,
    val dailyLimitMinutes: Int? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("manualBlocked", manualBlocked)
        .put("dailyLimitMinutes", dailyLimitMinutes ?: JSONObject.NULL)
}

class LocalPolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_policy", Context.MODE_PRIVATE)

    fun savePolicies(policies: Map<String, AppPolicy>) {
        val json = JSONObject()
        policies.forEach { (packageName, policy) -> json.put(packageName, policy.toJson()) }
        prefs.edit().putString("policies", json.toString()).apply()
    }

    fun loadPolicies(): Map<String, AppPolicy> {
        val raw = prefs.getString("policies", null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { packageName ->
                val item = json.optJSONObject(packageName) ?: return@forEach
                val limit = if (item.isNull("dailyLimitMinutes")) {
                    null
                } else {
                    item.optInt("dailyLimitMinutes").takeIf { it > 0 }
                }
                put(
                    packageName,
                    AppPolicy(
                        manualBlocked = item.optBoolean("manualBlocked", false),
                        dailyLimitMinutes = limit
                    )
                )
            }
        }
    }

    fun loadDailyLimitBlocks(): Set<String> {
        return prefs.getStringSet("dailyBlocks:${DateKeys.today()}", emptySet()).orEmpty()
    }

    fun markDailyLimitBlocked(packageName: String) {
        val key = "dailyBlocks:${DateKeys.today()}"
        val updated = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        updated.add(packageName)
        prefs.edit().putStringSet(key, updated).apply()
    }

    fun clearDailyLimitBlocks(packageName: String? = null) {
        val key = "dailyBlocks:${DateKeys.today()}"
        if (packageName == null) {
            prefs.edit().remove(key).apply()
        } else {
            val updated = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
            updated.remove(packageName)
            prefs.edit().putStringSet(key, updated).apply()
        }
    }

    fun loadUsageOffsetsMs(): Map<String, Long> {
        val day = DateKeys.today()
        val key = "usageOffsetsMs:$day"
        val existing = prefs.getString(key, null)
        if (existing != null) return parseLongMap(existing)

        val legacy = prefs.getString("usageOffsets:$day", null) ?: return emptyMap()
        val migrated = parseLongMap(legacy).mapValues { (_, minutes) -> minutes.coerceAtLeast(0L) * 60_000L }
        saveLongMap(key, migrated)
        return migrated
    }

    fun saveUsageOffsetMs(packageName: String, usageMs: Long) {
        val key = "usageOffsetsMs:${DateKeys.today()}"
        val values = parseLongMap(prefs.getString(key, "{}") ?: "{}").toMutableMap()
        values[packageName] = usageMs.coerceAtLeast(0L)
        saveLongMap(key, values)
    }

    private fun parseLongMap(raw: String): Map<String, Long> {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            json.keys().forEach { packageName ->
                put(packageName, json.optLong(packageName, 0L))
            }
        }
    }

    private fun saveLongMap(key: String, values: Map<String, Long>) {
        val json = JSONObject()
        values.forEach { (name, value) -> json.put(name, value) }
        prefs.edit().putString(key, json.toString()).apply()
    }

    fun saveActiveMode(modeId: String?, modeName: String?) {
        prefs.edit()
            .putString("activeModeId", modeId)
            .putString("activeModeName", modeName)
            .apply()
    }

    fun activeModeId(): String? = prefs.getString("activeModeId", null)

    fun activeModeName(): String? = prefs.getString("activeModeName", null)

    fun saveSafeMode(until: Long) {
        prefs.edit().putLong("safeModeUntil", until).apply()
    }

    fun safeModeUntil(): Long = prefs.getLong("safeModeUntil", 0L)

    fun isSafeModeActive(): Boolean = safeModeUntil() > System.currentTimeMillis()

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
