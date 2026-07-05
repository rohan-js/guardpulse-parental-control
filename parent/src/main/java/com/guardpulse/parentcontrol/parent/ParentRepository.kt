package com.guardpulse.parentcontrol.parent

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ServerValue
import com.guardpulse.parentcontrol.shared.FirebasePaths
import com.guardpulse.parentcontrol.shared.PinHasher
import com.guardpulse.parentcontrol.shared.PolicyConstants

class ParentRepository(
    private val database: DatabaseReference,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    fun createPairRequest(
        deviceId: String,
        secret: String?,
        manualCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Sign in before pairing a TV")
            return
        }
        database.child(FirebasePaths.pairRequests(deviceId)).push().setValue(
            mapOf(
                "parentUid" to uid,
                "secret" to secret,
                "code" to manualCode.ifBlank { null },
                "createdAt" to ServerValue.TIMESTAMP,
                "status" to "pending"
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Pair request failed")
        }
    }

    fun updatePolicy(
        deviceId: String,
        packageName: String,
        policy: ParentPolicy,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val value = mutableMapOf<String, Any?>(
            "packageName" to packageName,
            "manualBlocked" to policy.manualBlocked,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        policy.dailyLimitMinutes?.let { value["dailyLimitMinutes"] = it }
        database.child(FirebasePaths.devicePolicyApp(deviceId, packageName))
            .setValue(value)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Policy update failed") }
    }

    fun setPin(
        deviceId: String,
        pin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val hash = PinHasher.create(pin)
        database.child(FirebasePaths.deviceSecurityPin(deviceId)).setValue(
            mapOf(
                "salt" to hash.salt,
                "hash" to hash.hash,
                "updatedAt" to ServerValue.TIMESTAMP,
                "updatedBy" to auth.currentUser?.uid
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "PIN update failed")
        }
    }

    fun updateUnlock(
        deviceId: String,
        request: UnlockRequest,
        status: String,
        approvalType: String? = null,
        approvalDurationMs: Long? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val value = mutableMapOf<String, Any?>(
            "status" to status,
            "updatedAt" to ServerValue.TIMESTAMP,
            "updatedBy" to auth.currentUser?.uid
        )
        if (approvalType != null) value["approvalType"] = approvalType
        if (approvalDurationMs != null) value["approvalDurationMs"] = approvalDurationMs
        database.child(FirebasePaths.deviceUnlockRequest(deviceId, request.requestId)).updateChildren(value)
            .addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Unlock update failed")
        }
    }

    fun createMode(
        deviceId: String,
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val modeRef = database.child(FirebasePaths.devicePolicyModes(deviceId)).push()
        val modeId = modeRef.key ?: run {
            onError("Could not create mode")
            return
        }
        modeRef.setValue(
            mapOf(
                "modeId" to modeId,
                "name" to name,
                "createdAt" to ServerValue.TIMESTAMP,
                "updatedAt" to ServerValue.TIMESTAMP,
                "updatedBy" to auth.currentUser?.uid
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Mode create failed")
        }
    }

    fun updateModeName(
        deviceId: String,
        modeId: String,
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        database.child(FirebasePaths.devicePolicyMode(deviceId, modeId)).updateChildren(
            mapOf(
                "name" to name,
                "updatedAt" to ServerValue.TIMESTAMP,
                "updatedBy" to auth.currentUser?.uid
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Mode update failed")
        }
    }

    fun updateModePolicy(
        deviceId: String,
        modeId: String,
        packageName: String,
        policy: ParentPolicy,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val value = mutableMapOf<String, Any?>(
            "packageName" to packageName,
            "manualBlocked" to policy.manualBlocked,
            "updatedAt" to ServerValue.TIMESTAMP
        )
        policy.dailyLimitMinutes?.let { value["dailyLimitMinutes"] = it }
        database.child(FirebasePaths.devicePolicyModeApp(deviceId, modeId, packageName))
            .setValue(value)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Mode policy update failed") }
    }

    fun deleteMode(
        deviceId: String,
        modeId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        database.updateChildren(
            mapOf<String, Any?>(
                FirebasePaths.devicePolicyMode(deviceId, modeId) to null,
                FirebasePaths.devicePolicyActiveMode(deviceId) to null
            )
        )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Mode delete failed") }
    }

    fun setActiveMode(
        deviceId: String,
        mode: ParentMode?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val ref = database.child(FirebasePaths.devicePolicyActiveMode(deviceId))
        val task = if (mode == null) {
            ref.removeValue()
        } else {
            ref.setValue(
                mapOf(
                    "modeId" to mode.modeId,
                    "modeName" to mode.name,
                    "activatedAt" to ServerValue.TIMESTAMP,
                    "updatedBy" to auth.currentUser?.uid
                )
            )
        }
        task.addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Mode activation failed") }
    }

    fun startSafeMode(
        deviceId: String,
        durationMinutes: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val durationMs = durationMinutes.coerceIn(1, 1440) * 60_000L
        database.child(FirebasePaths.deviceSecuritySafeMode(deviceId)).setValue(
            mapOf(
                "enabled" to true,
                "until" to now + durationMs,
                "startedAt" to ServerValue.TIMESTAMP,
                "startedBy" to auth.currentUser?.uid
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Safe Mode failed")
        }
    }

    fun stopSafeMode(
        deviceId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        database.child(FirebasePaths.deviceSecuritySafeMode(deviceId)).setValue(
            mapOf(
                "enabled" to false,
                "until" to 0L,
                "updatedAt" to ServerValue.TIMESTAMP,
                "updatedBy" to auth.currentUser?.uid
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Safe Mode deactivation failed")
        }
    }

    fun sendCommand(
        deviceId: String,
        type: String,
        packageName: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Sign in before sending commands")
            return
        }
        val value = mutableMapOf<String, Any?>(
            "type" to type,
            "requestedBy" to uid,
            "createdAt" to ServerValue.TIMESTAMP
        )
        if (packageName != null) value["packageName"] = packageName
        database.child(FirebasePaths.deviceCommands(deviceId)).push().setValue(value)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Command failed") }
    }

    fun removePairedDevice(
        deviceId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError("Sign in before removing a TV")
            return
        }
        val commandKey = database.child(FirebasePaths.deviceCommands(deviceId)).push().key ?: run {
            onError("Could not create unpair command")
            return
        }
        database.updateChildren(
            mapOf(
                "${FirebasePaths.userDevice(uid, deviceId)}" to null,
                "${FirebasePaths.deviceCommands(deviceId)}/$commandKey/type" to PolicyConstants.COMMAND_UNPAIR,
                "${FirebasePaths.deviceCommands(deviceId)}/$commandKey/requestedBy" to uid,
                "${FirebasePaths.deviceCommands(deviceId)}/$commandKey/createdAt" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            onError(error.message ?: "Device removal failed")
        }
    }
}
