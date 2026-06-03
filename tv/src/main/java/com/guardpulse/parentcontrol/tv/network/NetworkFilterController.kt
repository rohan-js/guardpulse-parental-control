package com.guardpulse.parentcontrol.tv.network

import android.content.Context
import android.content.Intent

object NetworkFilterController {
    fun prepareIntent(@Suppress("UNUSED_PARAMETER") context: Context): Intent? = null

    fun isPrepared(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = false

    fun applyBlockedPackages(context: Context, @Suppress("UNUSED_PARAMETER") packages: Set<String>): NetworkFilterStatus {
        val appContext = context.applicationContext
        NetworkFilterStore.saveBlockedPackages(appContext, emptySet())
        val status = disabledStatus()
        NetworkFilterStore.saveStatus(appContext, status)
        return status
    }

    fun requestApply(context: Context) {
        val appContext = context.applicationContext
        NetworkFilterStore.saveBlockedPackages(appContext, emptySet())
        NetworkFilterStore.saveStatus(appContext, disabledStatus())
    }

    fun refreshPreparedStatus(context: Context): NetworkFilterStatus {
        val appContext = context.applicationContext
        NetworkFilterStore.saveBlockedPackages(appContext, emptySet())
        val status = disabledStatus()
        NetworkFilterStore.saveStatus(appContext, status)
        return status
    }

    private fun disabledStatus() = NetworkFilterStatus(
        prepared = false,
        active = false,
        blockedCount = 0,
        lastError = null
    )
}
