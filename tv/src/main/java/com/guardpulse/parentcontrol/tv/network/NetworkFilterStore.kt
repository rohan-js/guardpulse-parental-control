package com.guardpulse.parentcontrol.tv.network

import android.content.Context

data class NetworkFilterStatus(
    val prepared: Boolean = false,
    val active: Boolean = false,
    val blockedCount: Int = 0,
    val lastError: String? = null
)

object NetworkFilterStore {
    private const val PREFS = "network_filter"
    private const val KEY_BLOCKED_PACKAGES = "blockedPackages"
    private const val KEY_PREPARED = "prepared"
    private const val KEY_ACTIVE = "active"
    private const val KEY_BLOCKED_COUNT = "blockedCount"
    private const val KEY_LAST_ERROR = "lastError"

    fun saveBlockedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, packages.toSortedSet())
            .apply()
    }

    fun blockedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_BLOCKED_PACKAGES, emptySet()).orEmpty()
    }

    fun saveStatus(context: Context, status: NetworkFilterStatus) {
        prefs(context).edit()
            .putBoolean(KEY_PREPARED, status.prepared)
            .putBoolean(KEY_ACTIVE, status.active)
            .putInt(KEY_BLOCKED_COUNT, status.blockedCount)
            .putString(KEY_LAST_ERROR, status.lastError)
            .apply()
    }

    fun status(context: Context): NetworkFilterStatus {
        val prefs = prefs(context)
        return NetworkFilterStatus(
            prepared = prefs.getBoolean(KEY_PREPARED, false),
            active = prefs.getBoolean(KEY_ACTIVE, false),
            blockedCount = prefs.getInt(KEY_BLOCKED_COUNT, blockedPackages(context).size),
            lastError = prefs.getString(KEY_LAST_ERROR, null)
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
