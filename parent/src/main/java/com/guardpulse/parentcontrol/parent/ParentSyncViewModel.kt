package com.guardpulse.parentcontrol.parent

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.guardpulse.parentcontrol.shared.ControlPin
import com.guardpulse.parentcontrol.shared.ControlProtocol
import com.guardpulse.parentcontrol.shared.ControlSnapshotV2
import com.guardpulse.parentcontrol.shared.FirebaseRuntime
import com.guardpulse.parentcontrol.shared.FirebaseServerClock
import com.guardpulse.parentcontrol.shared.PolicyConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ParentSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val serverClock = FirebaseServerClock()
    private val handler = Handler(Looper.getMainLooper())
    private val selectionPrefs = application.getSharedPreferences("parent_sync", 0)
    private val firebaseStatus = FirebaseRuntime.initialize(application)
    private val database = firebaseStatus.takeIf { it.configured }
        ?.let { FirebaseDatabase.getInstance().reference }
    private val writer = database?.let { ParentRepository(it, auth, serverClock) }
    private val syncRepository = database?.let(::ParentSyncRepository)
    private val _state = MutableStateFlow(
        ParentSyncUiState(
            configured = firebaseStatus.configured,
            firebaseMessage = firebaseStatus.message,
            signedIn = auth.currentUser != null,
            message = firebaseStatus.message
        )
    )
    val state: StateFlow<ParentSyncUiState> = _state.asStateFlow()

    private var legacyPoliciesLoaded = false
    private var legacyModesLoaded = false
    private var legacyActiveModeLoaded = false
    private var legacySafeModeLoaded = false
    private var legacyPinLoaded = false
    private var controlExistenceLoaded = false
    private var legacyPin: ControlPin? = null
    private var legacyPolicies: Map<String, ParentPolicy> = emptyMap()
    private var legacyModes: List<ParentMode> = emptyList()
    private var legacyActiveMode: ActiveMode = ActiveMode()
    private var legacySafeMode: SafeModeState = SafeModeState()
    private var latestRuntimeStates: Map<String, ParentState> = emptyMap()
    private var migrationRequested = false
    private var pendingPairDeviceId: String? = selectionPrefs.getString("pendingPairDeviceId", null)
    private var pendingPairRequestId: String? = selectionPrefs.getString("pendingPairRequestId", null)
    private val pendingControlActions = ArrayDeque<() -> Unit>()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val signedIn = firebaseAuth.currentUser != null
        setState { it.copy(signedIn = signedIn, authBusy = false) }
        if (signedIn) {
            attachConnectionObserver()
            attachDeviceList()
            resumePairRequestObserver()
        } else {
            clearSignedOutState()
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            setState { it.copy(serverNow = serverClock.now()) }
            handler.postDelayed(this, 1_000L)
        }
    }

    init {
        serverClock.start()
        auth.addAuthStateListener(authListener)
        attachConnectionObserver()
        if (auth.currentUser != null) {
            attachDeviceList()
            resumePairRequestObserver()
        }
        handler.post(clockRunnable)
    }

    fun signIn(email: String, password: String) {
        if (!validateAuthInput(email, password)) return
        setState { it.copy(authBusy = true) }
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { setMessage("Signed in") }
            .addOnFailureListener { setMessage(it.message) }
            .addOnCompleteListener { setState { current -> current.copy(authBusy = false) } }
    }

    fun createAccount(email: String, password: String) {
        if (!validateAuthInput(email, password)) return
        setState { it.copy(authBusy = true) }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { setMessage("Account created") }
            .addOnFailureListener { setMessage(it.message) }
            .addOnCompleteListener { setState { current -> current.copy(authBusy = false) } }
    }

    fun signOut() {
        syncRepository?.close()
        auth.signOut()
    }

    fun selectDevice(deviceId: String) {
        selectionPrefs.edit().putString("selectedDeviceId", deviceId).apply()
        resetDeviceLoading(deviceId)
        attachDevice(deviceId)
    }

    fun createPairRequest(payload: String, manualDeviceId: String, manualCode: String) {
        val parsed = parsePairPayload(payload)
        val deviceId = parsed.first ?: manualDeviceId
        val secret = parsed.second
        if (deviceId.isBlank()) {
            setMessage("Enter a TV device ID or paste the QR payload.")
            return
        }
        if (secret.isNullOrBlank() && manualCode.isBlank()) {
            setMessage("Enter the 6-digit code or paste the QR payload.")
            return
        }
        writer?.createPairRequest(
            deviceId,
            secret,
            manualCode,
            onSuccess = { requestId ->
                pendingPairDeviceId = deviceId
                pendingPairRequestId = requestId
                selectionPrefs.edit()
                    .putString("pendingPairDeviceId", deviceId)
                    .putString("pendingPairRequestId", requestId)
                    .apply()
                observePairRequest(deviceId, requestId)
                setMessage("Pair request sent; waiting for TV")
            },
            onError = ::setMessage
        )
    }

    fun updatePolicy(packageName: String, policy: ParentPolicy) {
        val current = state.value
        val deviceId = current.selectedDeviceId ?: return setMessage("Select a TV first")
        val app = current.apps[packageName]
        if (app?.blockable == false) return setMessage("This app is protected: ${app.protectedReason ?: "not blockable"}")
        if (policy.dailyLimitMinutes != null && policy.dailyLimitMinutes !in 1..1440) {
            return setMessage("Daily limit must be between 1 and 1440 minutes")
        }
        runWhenControlReady {
            writer?.updatePolicy(deviceId, packageName, policy, ::controlSent, ::setMessage)
        }
    }

    fun setPin(pin: String) {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV before setting a PIN")
        if (!pin.matches(Regex("\\d{6}"))) return setMessage("PIN must be 6 digits")
        runWhenControlReady { writer?.setPin(deviceId, pin, ::controlSent, ::setMessage) }
    }

    fun updateUnlock(
        request: UnlockRequest,
        status: String,
        approvalType: String? = null,
        approvalDurationMs: Long? = null
    ) {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV first")
        if (status == PolicyConstants.UNLOCK_APPROVED &&
            request.expiresAt != null &&
            serverClock.now() > request.expiresAt
        ) {
            writer?.updateUnlock(
                deviceId,
                request,
                PolicyConstants.UNLOCK_EXPIRED,
                onSuccess = { setMessage("Unlock request expired") },
                onError = ::setMessage
            )
            return
        }
        writer?.updateUnlock(
            deviceId,
            request,
            status,
            approvalType,
            approvalDurationMs,
            onSuccess = {
                setMessage(
                    when {
                        status == PolicyConstants.UNLOCK_APPROVED && approvalType == PolicyConstants.UNLOCK_APPROVAL_TIMED ->
                            "Unlock sent for ${(approvalDurationMs ?: 0L) / 60_000L} minutes; waiting for TV"
                        status == PolicyConstants.UNLOCK_APPROVED -> "One-visit unlock sent; waiting for TV"
                        else -> "Unlock denied"
                    }
                )
            },
            onError = ::setMessage
        )
    }

    fun createMode(name: String) {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV first")
        val trimmed = name.trim()
        if (trimmed.isBlank()) return setMessage("Mode name cannot be empty")
        runWhenControlReady { writer?.createMode(deviceId, trimmed, ::controlSent, ::setMessage) }
    }

    fun renameMode(modeId: String, name: String) {
        val deviceId = state.value.selectedDeviceId ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return setMessage("Mode name cannot be empty")
        runWhenControlReady {
            writer?.updateModeName(deviceId, modeId, trimmed, ::controlSent, ::setMessage)
        }
    }

    fun deleteMode(modeId: String) {
        val current = state.value
        val deviceId = current.selectedDeviceId ?: return
        runWhenControlReady {
            writer?.deleteMode(deviceId, modeId, current.activeMode.modeId, ::controlSent, ::setMessage)
        }
    }

    fun updateModePolicy(modeId: String, packageName: String, policy: ParentPolicy) {
        val current = state.value
        val deviceId = current.selectedDeviceId ?: return setMessage("Select a TV first")
        if (current.apps[packageName]?.blockable == false) {
            return setMessage("This app is protected: ${current.apps[packageName]?.protectedReason ?: "not blockable"}")
        }
        if (policy.dailyLimitMinutes != null && policy.dailyLimitMinutes !in 1..1440) {
            return setMessage("Daily limit must be between 1 and 1440 minutes")
        }
        runWhenControlReady {
            writer?.updateModePolicy(deviceId, modeId, packageName, policy, ::controlSent, ::setMessage)
        }
    }

    fun setActiveMode(mode: ParentMode?) {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV first")
        runWhenControlReady { writer?.setActiveMode(deviceId, mode, ::controlSent, ::setMessage) }
    }

    fun startSafeMode(durationMinutes: Int) {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV first")
        if (durationMinutes !in 1..1440) return setMessage("Safe Mode duration must be between 1 and 1440 minutes")
        runWhenControlReady {
            writer?.startSafeMode(deviceId, durationMinutes, ::controlSent, ::setMessage)
        }
    }

    fun stopSafeMode() {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV first")
        runWhenControlReady { writer?.stopSafeMode(deviceId, ::controlSent, ::setMessage) }
    }

    fun sendCommand(type: String, packageName: String? = null) {
        val deviceId = state.value.selectedDeviceId ?: return setMessage("Select a TV first")
        writer?.sendCommand(
            deviceId,
            type,
            packageName,
            onSuccess = { setMessage("Command sent; waiting for TV") },
            onError = ::setMessage
        )
    }

    fun removePairedDevice(deviceId: String) {
        writer?.removePairedDevice(
            deviceId,
            onSuccess = { setMessage("Removal requested; waiting for TV") },
            onError = ::setMessage
        )
    }

    fun reconnect() {
        setMessage("Reconnecting...")
        auth.currentUser?.getIdToken(true)
            ?.addOnCompleteListener {
                syncRepository?.refresh()
                setMessage(if (it.isSuccessful) "Reconnected" else it.exception?.message)
            }
            ?: syncRepository?.refresh()
    }

    private fun attachDeviceList() {
        val uid = auth.currentUser?.uid ?: return
        setState { it.copy(loadingDevices = true) }
        syncRepository?.observeDevices(
            uid,
            onDevices = { devices ->
                setState { current -> current.copy(devices = devices, loadingDevices = false) }
                val selected = state.value.selectedDeviceId
                    ?.takeIf { id -> devices.any { it.deviceId == id } }
                    ?: selectionPrefs.getString("selectedDeviceId", null)
                        ?.takeIf { id -> devices.any { it.deviceId == id } }
                    ?: devices.singleOrNull()?.deviceId
                if (selected != null && selected != state.value.selectedDeviceId) {
                    selectDevice(selected)
                } else if (selected == null && state.value.selectedDeviceId != null) {
                    clearSelectedDevice()
                }
            },
            onError = ::setMessage
        )
    }

    private fun attachConnectionObserver() {
        syncRepository?.observeConnection { connected ->
            setState { it.copy(phoneConnected = connected) }
            if (connected && auth.currentUser != null) syncRepository.refresh()
        }
    }

    private fun attachDevice(deviceId: String) {
        syncRepository?.observeDevice(deviceId, object : ParentSyncRepository.DeviceObserver {
            override fun onApps(value: Map<String, ParentApp>) {
                setState { it.copy(apps = value, loadingDeviceDetails = false) }
            }

            override fun onPolicies(value: Map<String, ParentPolicy>) {
                legacyPoliciesLoaded = true
                legacyPolicies = value
                setState { current ->
                    if (current.confirmedControl == null) current.copy(policies = value) else current
                }
                maybeSeedControlV2()
            }

            override fun onModes(value: List<ParentMode>) {
                legacyModesLoaded = true
                legacyModes = value
                setState { current ->
                    if (current.confirmedControl == null) current.copy(modes = value) else current
                }
                maybeSeedControlV2()
            }

            override fun onActiveMode(value: ActiveMode) {
                legacyActiveModeLoaded = true
                legacyActiveMode = value
                setState { current ->
                    if (current.confirmedControl == null) current.copy(activeMode = value) else current
                }
                maybeSeedControlV2()
            }

            override fun onSafeMode(value: SafeModeState) {
                legacySafeModeLoaded = true
                legacySafeMode = value
                setState { current ->
                    if (current.confirmedControl == null) current.copy(safeMode = value) else current
                }
                maybeSeedControlV2()
            }

            override fun onPin(value: ControlPin?) {
                legacyPinLoaded = true
                legacyPin = value
                maybeSeedControlV2()
            }

            override fun onStates(value: Map<String, ParentState>) {
                latestRuntimeStates = value
                setState { it.copy(states = value) }
                promoteConfirmedRuntimeStates()
            }
            override fun onSecurity(value: SecurityRuntime) = setState { it.copy(security = value) }
            override fun onUnlockRequests(value: List<UnlockRequest>) = setState { it.copy(unlockRequests = value) }
            override fun onTamperEvents(value: List<TamperEvent>) = setState { it.copy(tamperEvents = value) }
            override fun onCommands(value: List<ParentCommand>) = setState { it.copy(commands = value) }

            override fun onDesiredRevision(snapshot: com.google.firebase.database.DataSnapshot) {
                setState { it.copy(desiredRevision = ControlProtocol.parseDesired(snapshot)) }
            }

            override fun onAppliedRevision(value: com.guardpulse.parentcontrol.shared.SyncAppliedRevision) {
                setState { it.copy(appliedRevision = value) }
                promoteConfirmedControl()
            }

            override fun onSyncRuntime(value: com.guardpulse.parentcontrol.shared.SyncRuntimeState) {
                setState { it.copy(syncRuntime = value) }
            }

            override fun onControlV2(exists: Boolean, value: ControlSnapshotV2?) {
                controlExistenceLoaded = true
                setState { it.copy(controlV2Exists = exists, desiredControl = value) }
                if (exists) {
                    migrationRequested = true
                    flushPendingControlActions()
                    promoteConfirmedControl()
                } else {
                    maybeSeedControlV2()
                }
            }

            override fun onError(message: String) = setMessage(message)
        })
    }

    private fun maybeSeedControlV2() {
        val current = state.value
        if (migrationRequested || current.controlV2Exists || !controlExistenceLoaded) return
        if (!legacyPoliciesLoaded || !legacyModesLoaded || !legacyActiveModeLoaded ||
            !legacySafeModeLoaded || !legacyPinLoaded
        ) return
        val deviceId = current.selectedDeviceId ?: return
        migrationRequested = true
        writer?.seedControlV2(
            deviceId,
            legacyPolicies,
            legacyModes,
            legacyActiveMode,
            legacySafeMode,
            legacyPin,
            onSuccess = { setMessage("TV controls upgraded; waiting for TV acknowledgement") },
            onError = {
                migrationRequested = false
                setMessage(it)
            }
        )
    }

    private fun resetDeviceLoading(deviceId: String) {
        legacyPoliciesLoaded = false
        legacyModesLoaded = false
        legacyActiveModeLoaded = false
        legacySafeModeLoaded = false
        legacyPinLoaded = false
        controlExistenceLoaded = false
        legacyPin = null
        legacyPolicies = emptyMap()
        legacyModes = emptyList()
        legacyActiveMode = ActiveMode()
        legacySafeMode = SafeModeState()
        latestRuntimeStates = emptyMap()
        migrationRequested = false
        pendingControlActions.clear()
        setState {
            it.copy(
                selectedDeviceId = deviceId,
                apps = emptyMap(),
                policies = emptyMap(),
                states = emptyMap(),
                confirmedStates = emptyMap(),
                modes = emptyList(),
                activeMode = ActiveMode(),
                safeMode = SafeModeState(),
                security = SecurityRuntime(),
                unlockRequests = emptyList(),
                tamperEvents = emptyList(),
                commands = emptyList(),
                desiredRevision = null,
                appliedRevision = com.guardpulse.parentcontrol.shared.SyncAppliedRevision(),
                desiredControl = null,
                confirmedControl = null,
                syncRuntime = com.guardpulse.parentcontrol.shared.SyncRuntimeState(),
                controlV2Exists = false,
                loadingDeviceDetails = true
            )
        }
    }

    private fun clearSelectedDevice() {
        selectionPrefs.edit().remove("selectedDeviceId").apply()
        pendingControlActions.clear()
        syncRepository?.clearSelectedDevice()
        setState {
            it.copy(
                selectedDeviceId = null,
                apps = emptyMap(),
                policies = emptyMap(),
                states = emptyMap(),
                confirmedStates = emptyMap(),
                modes = emptyList(),
                activeMode = ActiveMode(),
                safeMode = SafeModeState(),
                security = SecurityRuntime(),
                unlockRequests = emptyList(),
                tamperEvents = emptyList(),
                commands = emptyList(),
                desiredRevision = null,
                appliedRevision = com.guardpulse.parentcontrol.shared.SyncAppliedRevision(),
                desiredControl = null,
                confirmedControl = null,
                syncRuntime = com.guardpulse.parentcontrol.shared.SyncRuntimeState(),
                controlV2Exists = false,
                loadingDeviceDetails = false
            )
        }
    }

    private fun clearSignedOutState() {
        selectionPrefs.edit()
            .remove("selectedDeviceId")
            .remove("pendingPairDeviceId")
            .remove("pendingPairRequestId")
            .apply()
        pendingPairDeviceId = null
        pendingPairRequestId = null
        setState {
            ParentSyncUiState(
                configured = firebaseStatus.configured,
                firebaseMessage = firebaseStatus.message,
                phoneConnected = it.phoneConnected,
                serverNow = serverClock.now(),
                message = it.message
            )
        }
    }

    private fun validateAuthInput(email: String, password: String): Boolean {
        if (email.isBlank()) {
            setMessage("Enter an email address")
            return false
        }
        if (password.length < 6) {
            setMessage("Password must be at least 6 characters")
            return false
        }
        return true
    }

    private fun parsePairPayload(payload: String): Pair<String?, String?> {
        if (payload.isBlank()) return null to null
        return runCatching {
            val uri = Uri.parse(payload)
            uri.getQueryParameter("deviceId") to uri.getQueryParameter("secret")
        }.getOrElse { null to null }
    }

    private fun resumePairRequestObserver() {
        val deviceId = pendingPairDeviceId ?: return
        val requestId = pendingPairRequestId ?: return
        observePairRequest(deviceId, requestId)
    }

    private fun observePairRequest(deviceId: String, requestId: String) {
        syncRepository?.observePairRequest(
            deviceId,
            requestId,
            onValue = { request ->
                setState { it.copy(pairRequest = request) }
                when (request?.status) {
                    PolicyConstants.PAIR_ACCEPTED -> {
                        clearPersistedPairRequest()
                        setMessage("TV pairing confirmed")
                        attachDeviceList()
                    }
                    PolicyConstants.PAIR_REJECTED -> {
                        clearPersistedPairRequest()
                        setMessage(request.error ?: "TV rejected the pairing request")
                    }
                    PolicyConstants.PAIR_EXPIRED -> {
                        clearPersistedPairRequest()
                        setMessage("Pairing request expired")
                    }
                    PolicyConstants.PAIR_FAILED -> {
                        clearPersistedPairRequest()
                        setMessage(request.error ?: "TV pairing failed")
                    }
                }
            },
            onError = ::setMessage
        )
    }

    private fun clearPersistedPairRequest() {
        pendingPairDeviceId = null
        pendingPairRequestId = null
        selectionPrefs.edit()
            .remove("pendingPairDeviceId")
            .remove("pendingPairRequestId")
            .apply()
        syncRepository?.clearPairRequestObserver()
    }

    private fun controlSent() = setMessage("Sent to TV; waiting for acknowledgement")

    private fun promoteConfirmedControl() {
        val current = state.value
        val desired = current.desiredControl ?: return
        val applied = current.appliedRevision
        if (applied.revisionId != desired.revisionId ||
            applied.status != PolicyConstants.SYNC_STATUS_APPLIED
        ) return
        setState {
            it.copy(
                confirmedControl = desired,
                policies = desired.toParentPolicies(),
                modes = desired.toParentModes(),
                activeMode = desired.toParentActiveMode(),
                safeMode = desired.toParentSafeMode(),
                confirmedStates = matchingRuntimeStates(
                    desired.revisionId,
                    latestRuntimeStates
                )
            )
        }
    }

    private fun promoteConfirmedRuntimeStates() {
        val confirmedRevision = state.value.confirmedControl?.revisionId ?: return
        val matching = matchingRuntimeStates(confirmedRevision, latestRuntimeStates)
        if (matching.isEmpty()) return
        setState { it.copy(confirmedStates = matching) }
    }

    private fun matchingRuntimeStates(
        revisionId: String,
        runtimeStates: Map<String, ParentState>
    ): Map<String, ParentState> = runtimeStates.filterValues { it.controlRevisionId == revisionId }

    private fun runWhenControlReady(action: () -> Unit) {
        if (state.value.controlV2Exists) {
            action()
            return
        }
        if (pendingControlActions.size >= 20) pendingControlActions.removeFirst()
        pendingControlActions.addLast(action)
        setMessage("Preparing synchronized TV controls; your change is queued")
        maybeSeedControlV2()
    }

    private fun flushPendingControlActions() {
        while (pendingControlActions.isNotEmpty()) {
            pendingControlActions.removeFirst().invoke()
        }
    }

    private fun setMessage(message: String?) = setState { it.copy(message = message) }

    private inline fun setState(transform: (ParentSyncUiState) -> ParentSyncUiState) {
        _state.value = transform(_state.value)
    }

    override fun onCleared() {
        handler.removeCallbacksAndMessages(null)
        auth.removeAuthStateListener(authListener)
        syncRepository?.close()
        serverClock.stop()
        super.onCleared()
    }
}
