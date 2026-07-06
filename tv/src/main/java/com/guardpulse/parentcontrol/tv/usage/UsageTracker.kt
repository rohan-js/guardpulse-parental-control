package com.guardpulse.parentcontrol.tv.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.guardpulse.parentcontrol.shared.DateKeys
import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.guardpulse.parentcontrol.tv.fallback.LiveForegroundSession
import java.util.Calendar

class UsageTracker(private val context: Context) {
    private val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun rawUsageMillisToday(): Map<String, Long> {
        if (!hasUsageAccess()) return emptyMap()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        val usageByPackage = mutableMapOf<String, Long>()
        stats
            .filter { it.totalTimeInForeground > 0 }
            .forEach { stat ->
                val packageName = canonicalUsagePackage(stat.packageName)
                usageByPackage[packageName] = (usageByPackage[packageName] ?: 0L) + stat.totalTimeInForeground
            }
        return usageByPackage
    }

    fun effectiveUsageMillisToday(
        liveSession: LiveForegroundSession? = null,
        now: Long = System.currentTimeMillis()
    ): Map<String, Long> {
        return applyLiveForegroundSession(rawUsageMillisToday(), liveSession, now, DateKeys.today())
    }

    fun usageMinutesToday(liveSession: LiveForegroundSession? = null): Map<String, Long> {
        return effectiveUsageMillisToday(liveSession)
            .mapValues { (_, usageMs) -> (usageMs / MILLIS_PER_MINUTE).coerceAtLeast(0L) }
    }

    private fun canonicalUsagePackage(packageName: String): String {
        return PolicyConstants.sourceLockPolicyPackage(packageName) ?: packageName
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L

        internal fun applyLiveForegroundSession(
            baselineUsageMs: Map<String, Long>,
            liveSession: LiveForegroundSession?,
            now: Long,
            today: String
        ): Map<String, Long> {
            if (liveSession == null || liveSession.dayKey != today) return baselineUsageMs
            val elapsedMs = (now - liveSession.startedAt).coerceAtLeast(0L)
            val liveUsageMs = liveSession.baselineUsageMs + elapsedMs
            val currentUsageMs = baselineUsageMs[liveSession.packageName] ?: 0L
            val effectiveUsageMs = maxOf(currentUsageMs, liveUsageMs)
            return baselineUsageMs.toMutableMap().apply {
                put(liveSession.packageName, effectiveUsageMs)
            }
        }
    }
}
