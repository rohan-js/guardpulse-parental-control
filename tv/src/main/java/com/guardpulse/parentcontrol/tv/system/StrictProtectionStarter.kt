package com.guardpulse.parentcontrol.tv.system

import android.content.Context
import com.guardpulse.parentcontrol.tv.network.NetworkFilterController
import com.guardpulse.parentcontrol.tv.sync.TvSyncService

object StrictProtectionStarter {
    fun recover(context: Context, action: String? = TvSyncService.ACTION_RECONCILE) {
        val appContext = context.applicationContext
        runCatching { TvServiceStarter.start(appContext, action) }
        runCatching {
            NetworkFilterController.applyBlockedPackages(appContext, emptySet())
        }
    }
}
