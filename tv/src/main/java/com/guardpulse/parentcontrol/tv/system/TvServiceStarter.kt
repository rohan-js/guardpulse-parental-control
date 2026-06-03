package com.guardpulse.parentcontrol.tv.system

import android.content.Context
import android.content.Intent
import android.os.Build
import com.guardpulse.parentcontrol.tv.sync.TvSyncService

object TvServiceStarter {
    fun start(context: Context, action: String? = null) {
        val intent = Intent(context, TvSyncService::class.java).apply {
            if (action != null) this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
