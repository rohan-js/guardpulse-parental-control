package com.guardpulse.parentcontrol.tv.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.guardpulse.parentcontrol.tv.sync.TvSyncService

class PackageChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StrictProtectionStarter.recover(context.applicationContext, TvSyncService.ACTION_RESCAN_APPS)
    }
}
