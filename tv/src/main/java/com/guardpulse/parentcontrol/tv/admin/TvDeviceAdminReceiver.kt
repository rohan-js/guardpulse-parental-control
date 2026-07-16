package com.guardpulse.parentcontrol.tv.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.guardpulse.parentcontrol.tv.fallback.FallbackProtection
import com.guardpulse.parentcontrol.tv.sync.TamperEventQueue

class TvDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        uploadTamper(
            context.applicationContext,
            PolicyConstants.TAMPER_ADMIN_DISABLE_REQUESTED,
            "Device Admin deactivation was requested on the TV"
        )
        FallbackProtection.openLock(
            context.applicationContext,
            context.packageName,
            PolicyConstants.TAMPER_ADMIN_DISABLE_REQUESTED
        )
        return "Device protection will be disabled. Enter the parent PIN before changing this setting."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        uploadTamper(
            context.applicationContext,
            PolicyConstants.TAMPER_ADMIN_DISABLED,
            "Device Admin was disabled on the TV"
        )
    }

    private fun uploadTamper(context: Context, type: String, message: String) {
        TamperEventQueue.enqueue(context, type, message)
    }
}
