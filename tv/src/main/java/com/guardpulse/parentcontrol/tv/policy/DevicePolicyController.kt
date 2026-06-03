package com.guardpulse.parentcontrol.tv.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import com.guardpulse.parentcontrol.tv.admin.TvDeviceAdminReceiver

data class PackageApplyResult(
    val packageName: String,
    val requestedSuspended: Boolean,
    val applied: Boolean,
    val error: String? = null
)

class DevicePolicyController(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, TvDeviceAdminReceiver::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(context.packageName)

    fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    fun applyHardening(): List<String> {
        if (!isDeviceOwner()) return listOf("App is not Device Owner")

        val results = mutableListOf<String>()
        runPolicy("Block TV app uninstall") {
            dpm.setUninstallBlocked(admin, context.packageName, true)
        }?.let(results::add)

        listOf(
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_DEBUGGING_FEATURES
        ).forEach { restriction ->
            runPolicy("Apply $restriction") {
                dpm.addUserRestriction(admin, restriction)
            }?.let(results::add)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runPolicy("Block unknown-source installs globally") {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }?.let(results::add)
        } else {
            runPolicy("Block unknown-source installs") {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            }?.let(results::add)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runPolicy("Disable user controls for TV app") {
                dpm.setUserControlDisabledPackages(admin, listOf(context.packageName))
            }?.let(results::add)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runPolicy("Enable always-on lockdown VPN") {
                dpm.setAlwaysOnVpnPackage(admin, context.packageName, true)
            }?.let(results::add)
        }

        return results.ifEmpty { listOf("Hardening policies applied") }
    }

    fun setSuspended(packageName: String, suspended: Boolean): PackageApplyResult {
        if (!isDeviceOwner()) {
            return PackageApplyResult(packageName, suspended, applied = false, error = "Not Device Owner")
        }

        return try {
            val failed = dpm.setPackagesSuspended(admin, arrayOf(packageName), suspended)
            if (failed.isEmpty()) {
                PackageApplyResult(packageName, suspended, applied = true)
            } else {
                PackageApplyResult(
                    packageName,
                    suspended,
                    applied = false,
                    error = "Android refused package suspension"
                )
            }
        } catch (error: Exception) {
            PackageApplyResult(packageName, suspended, applied = false, error = error.message)
        }
    }

    fun isSuspended(packageName: String): Boolean {
        return try {
            dpm.isPackageSuspended(admin, packageName)
        } catch (_: Exception) {
            false
        }
    }

    private inline fun runPolicy(label: String, block: () -> Unit): String? {
        return try {
            block()
            null
        } catch (error: Exception) {
            "$label failed: ${error.message}"
        }
    }
}
