package com.guardpulse.parentcontrol.tv

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.guardpulse.parentcontrol.tv.fallback.FallbackStateStore
import com.guardpulse.parentcontrol.tv.sync.PolicyReconcileWorker
import com.guardpulse.parentcontrol.tv.system.StrictProtectionStarter
import java.util.concurrent.TimeUnit

class TvParentalControlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FallbackStateStore(this).clearSetupVisitUnlock()
        val work = PeriodicWorkRequest.Builder(
            PolicyReconcileWorker::class.java,
            15,
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "policy-reconcile",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
        runCatching { StrictProtectionStarter.recover(this) }
    }
}
