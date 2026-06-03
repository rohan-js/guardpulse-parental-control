package com.guardpulse.parentcontrol.tv.apps

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.guardpulse.parentcontrol.shared.PolicyConstants

data class TvInstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val systemApp: Boolean,
    val blockable: Boolean,
    val protectedReason: String?
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "label" to label,
        "versionName" to versionName,
        "versionCode" to versionCode,
        "systemApp" to systemApp,
        "blockable" to blockable,
        "protectedReason" to protectedReason
    )
}

class AppInventoryProvider(private val context: Context) {
    private val packageManager = context.packageManager
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)

    fun listLaunchableApps(): List<TvInstalledApp> {
        val packageNames = linkedSetOf<String>()
        queryLaunchable(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach(packageNames::add)
        queryLaunchable(Intent.CATEGORY_LAUNCHER).forEach(packageNames::add)
        PolicyConstants.parentVisibleLockPackages.forEach(packageNames::add)

        val launcherPackages = queryHomePackages()
        val activeAdmins = dpm.activeAdmins?.map { it.packageName }?.toSet().orEmpty()

        return packageNames.mapNotNull { packageName ->
            PolicyConstants.settingsSectionPolicy(packageName)?.let { section ->
                return@mapNotNull TvInstalledApp(
                    packageName = packageName,
                    label = section.label,
                    versionName = "",
                    versionCode = 0L,
                    systemApp = true,
                    blockable = true,
                    protectedReason = null
                )
            }
            runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val protectedReason = protectedReason(packageName, launcherPackages, activeAdmins)
                TvInstalledApp(
                    packageName = packageName,
                    label = displayLabel(packageName, info),
                    versionName = packageInfo.versionName ?: "",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    },
                    systemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    blockable = protectedReason == null,
                    protectedReason = protectedReason
                )
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    private fun queryLaunchable(category: String): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
    }

    private fun queryHomePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun protectedReason(
        packageName: String,
        launcherPackages: Set<String>,
        activeAdmins: Set<String>
    ): String? {
        return when {
            packageName in PolicyConstants.parentVisibleLockPackages -> null
            packageName == context.packageName -> "control app"
            packageName in launcherPackages -> "active launcher"
            packageName in activeAdmins -> "device admin"
            packageName in PolicyConstants.alwaysProtectedPackages -> "system critical"
            else -> null
        }
    }

    private fun displayLabel(packageName: String, info: ApplicationInfo): String {
        return when {
            packageName in PolicyConstants.sourceLockPackages -> "Live TV"
            packageName in PolicyConstants.primarySettingsPackages -> "Settings"
            packageName in PolicyConstants.settingsSectionLockPackages ->
                PolicyConstants.settingsSectionPolicy(packageName)?.label ?: packageName
            else -> packageManager.getApplicationLabel(info).toString()
        }
    }
}
