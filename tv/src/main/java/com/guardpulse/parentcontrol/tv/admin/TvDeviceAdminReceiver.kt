package com.guardpulse.parentcontrol.tv.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.guardpulse.parentcontrol.shared.DeviceIdentity
import com.guardpulse.parentcontrol.shared.FirebaseBootstrap
import com.guardpulse.parentcontrol.shared.FirebasePaths
import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.guardpulse.parentcontrol.tv.fallback.FallbackProtection

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
        val status = FirebaseBootstrap.initialize(context)
        if (!status.configured) return
        val writeEvent = {
            val deviceId = DeviceIdentity.getOrCreate(context)
            FirebaseDatabase.getInstance().reference
                .child(FirebasePaths.deviceTamperEvents(deviceId))
                .push()
                .setValue(
                    mapOf(
                        "type" to type,
                        "createdAt" to ServerValue.TIMESTAMP,
                        "message" to message
                    )
                )
        }
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnSuccessListener { writeEvent() }
        } else {
            writeEvent()
        }
    }
}
