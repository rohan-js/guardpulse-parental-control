package com.guardpulse.parentcontrol.tv.sync

import android.content.Context
import org.json.JSONArray

class TvSyncLocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv_sync_runtime", Context.MODE_PRIVATE)

    fun activateV2(revisionId: String) {
        prefs.edit()
            .putBoolean("v2Activated", true)
            .putString("lastV2Revision", revisionId)
            .apply()
    }

    fun isV2Activated(): Boolean = prefs.getBoolean("v2Activated", false)

    fun lastV2Revision(): String? = prefs.getString("lastV2Revision", null)

    fun savePendingAppliedRevision(revisionId: String?) {
        prefs.edit().putString("pendingAppliedRevision", revisionId).apply()
    }

    fun pendingAppliedRevision(): String? = prefs.getString("pendingAppliedRevision", null)

    fun saveLastError(channel: String?, message: String?) {
        prefs.edit()
            .putString("lastFailedChannel", channel)
            .putString("lastError", message)
            .putLong("lastErrorAt", if (message == null) 0L else System.currentTimeMillis())
            .apply()
    }

    fun lastFailedChannel(): String? = prefs.getString("lastFailedChannel", null)
    fun lastError(): String? = prefs.getString("lastError", null)
    fun lastErrorAt(): Long = prefs.getLong("lastErrorAt", 0L)

    fun isCommandProcessed(commandId: String): Boolean = commandId in processedCommands()

    fun markCommandProcessed(commandId: String) {
        val commands = processedCommands().toMutableList()
        commands.remove(commandId)
        commands.add(commandId)
        while (commands.size > MAX_PROCESSED_COMMANDS) commands.removeAt(0)
        prefs.edit().putString("processedCommands", JSONArray(commands).toString()).apply()
    }

    private fun processedCommands(): List<String> {
        val raw = prefs.getString("processedCommands", "[]") ?: "[]"
        val json = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until json.length()) {
                json.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    companion object {
        private const val MAX_PROCESSED_COMMANDS = 100
    }
}
