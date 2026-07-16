package com.guardpulse.parentcontrol.tv.sync

import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.guardpulse.parentcontrol.shared.ControlProtocol
import com.guardpulse.parentcontrol.shared.ControlSnapshotV2
import com.guardpulse.parentcontrol.shared.FirebasePaths
import com.guardpulse.parentcontrol.shared.SyncDesiredRevision
import java.util.UUID

class TvSyncEngine(
    private val database: FirebaseDatabase,
    private val deviceId: String,
    private val localStore: TvSyncLocalStore,
    private val callback: Callback
) {
    interface Callback {
        fun onConnectionChanged(connected: Boolean, sessionId: String?)
        fun onControlReady(snapshot: ControlSnapshotV2, desired: SyncDesiredRevision?, generation: Long)
        fun onControlRejected(revisionId: String?, error: String)
        fun onSyncListenerError(channel: String, error: DatabaseError)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val registrations = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private var pendingSnapshot: ControlSnapshotV2? = null
    private var pendingDesired: SyncDesiredRevision? = null
    private var generation = 0L
    private var sessionId: String? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private var started = false
    private val reconcileRunnable = Runnable(::dispatchNewestControl)
    private val retryRunnable = Runnable {
        detachListeners()
        attachListeners()
    }

    fun start() {
        if (started) return
        started = true
        attachListeners()
    }

    fun stop() {
        started = false
        handler.removeCallbacksAndMessages(null)
        detachListeners()
    }

    fun usesV2(): Boolean = localStore.isV2Activated()

    fun currentSessionId(): String? = sessionId

    fun isCurrent(generation: Long, revisionId: String): Boolean {
        return this.generation == generation && pendingSnapshot?.revisionId == revisionId
    }

    private fun attachListeners() {
        if (!started || registrations.isNotEmpty()) return
        register(
            database.getReference(".info/connected"),
            "connection"
        ) { snapshot ->
            val connected = snapshot.getValue(Boolean::class.java) ?: false
            if (connected) {
                retryDelayMs = INITIAL_RETRY_MS
                sessionId = UUID.randomUUID().toString()
            }
            callback.onConnectionChanged(connected, sessionId)
        }
        register(
            database.getReference(FirebasePaths.deviceControlV2(deviceId)),
            "controlV2"
        ) { snapshot ->
            if (!snapshot.exists()) {
                if (localStore.isV2Activated()) {
                    callback.onControlRejected(localStore.lastV2Revision(), "V2 control snapshot was removed")
                }
                return@register
            }
            ControlProtocol.parse(snapshot)
                .onSuccess { control ->
                    pendingSnapshot = control
                    localStore.activateV2(control.revisionId)
                    scheduleReconcile()
                }
                .onFailure { error ->
                    callback.onControlRejected(
                        snapshot.child("revisionId").getValue(String::class.java),
                        error.message ?: "Invalid V2 control snapshot"
                    )
                }
        }
        register(
            database.getReference(FirebasePaths.deviceSyncDesired(deviceId)),
            "desiredRevision"
        ) { snapshot ->
            pendingDesired = ControlProtocol.parseDesired(snapshot)
            scheduleReconcile()
        }
    }

    private fun register(
        ref: DatabaseReference,
        channel: String,
        onData: (DataSnapshot) -> Unit
    ) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handler.post { onData(snapshot) }
            }

            override fun onCancelled(error: DatabaseError) {
                handler.post {
                    callback.onSyncListenerError(channel, error)
                    scheduleRetry()
                }
            }
        }
        registrations += ref to listener
        ref.addValueEventListener(listener)
    }

    private fun scheduleReconcile() {
        handler.removeCallbacks(reconcileRunnable)
        handler.postDelayed(reconcileRunnable, CONTROL_DEBOUNCE_MS)
    }

    private fun dispatchNewestControl() {
        val control = pendingSnapshot ?: return
        val desired = pendingDesired
        if (desired != null && desired.revisionId != control.revisionId) {
            handler.postDelayed(reconcileRunnable, REVISION_SETTLE_RETRY_MS)
            return
        }
        generation += 1
        callback.onControlReady(control, desired, generation)
    }

    private fun scheduleRetry() {
        if (!started) return
        handler.removeCallbacks(retryRunnable)
        handler.postDelayed(retryRunnable, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
    }

    private fun detachListeners() {
        registrations.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        registrations.clear()
    }

    companion object {
        private const val CONTROL_DEBOUNCE_MS = 250L
        private const val REVISION_SETTLE_RETRY_MS = 500L
        private const val INITIAL_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 5 * 60_000L
    }
}
