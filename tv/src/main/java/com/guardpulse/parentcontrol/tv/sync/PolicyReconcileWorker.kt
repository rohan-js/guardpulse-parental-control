package com.guardpulse.parentcontrol.tv.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.guardpulse.parentcontrol.tv.system.TvServiceStarter

class PolicyReconcileWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        TvServiceStarter.start(applicationContext, TvSyncService.ACTION_RECONCILE)
        return Result.success()
    }
}
