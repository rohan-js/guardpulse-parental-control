package com.guardpulse.parentcontrol.tv.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.guardpulse.parentcontrol.shared.DeviceIdentity
import com.guardpulse.parentcontrol.shared.DesiredAppPolicy
import com.guardpulse.parentcontrol.shared.FirebaseBootstrap
import com.guardpulse.parentcontrol.shared.FirebasePaths
import com.guardpulse.parentcontrol.shared.PackageKeys
import com.guardpulse.parentcontrol.shared.PolicyDecider
import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.guardpulse.parentcontrol.tv.MainActivity
import com.guardpulse.parentcontrol.tv.R
import com.guardpulse.parentcontrol.tv.apps.AppInventoryProvider
import com.guardpulse.parentcontrol.tv.apps.TvInstalledApp
import com.guardpulse.parentcontrol.tv.fallback.FallbackProtection
import com.guardpulse.parentcontrol.tv.fallback.FallbackStateStore
import com.guardpulse.parentcontrol.tv.fallback.PinRecord
import com.guardpulse.parentcontrol.tv.network.NetworkFilterController
import com.guardpulse.parentcontrol.tv.pairing.PairingManager
import com.guardpulse.parentcontrol.tv.policy.AppPolicy
import com.guardpulse.parentcontrol.tv.policy.DevicePolicyController
import com.guardpulse.parentcontrol.tv.policy.LocalPolicyStore
import com.guardpulse.parentcontrol.tv.system.BackgroundRestrictionStatus
import com.guardpulse.parentcontrol.tv.usage.UsageTracker

class TvSyncService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var deviceId: String
    private lateinit var policyController: DevicePolicyController
    private lateinit var inventoryProvider: AppInventoryProvider
    private lateinit var localPolicyStore: LocalPolicyStore
    private lateinit var usageTracker: UsageTracker
    private lateinit var pairingManager: PairingManager
    private lateinit var fallbackStore: FallbackStateStore

    private var db: DatabaseReference? = null
    private var startedFirebase = false
    private var listenersAttached = false
    private var authRetryDelayMs = 5_000L
    private var lastSyncError: String? = null
    private val valueListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()
    private val childListeners = mutableListOf<Pair<DatabaseReference, ChildEventListener>>()
    private val retryFirebaseRunnable = Runnable {
        startedFirebase = false
        startFirebaseIfConfigured()
    }

    override fun onCreate() {
        super.onCreate()
        deviceId = DeviceIdentity.getOrCreate(this)
        policyController = DevicePolicyController(this)
        inventoryProvider = AppInventoryProvider(this)
        localPolicyStore = LocalPolicyStore(this)
        usageTracker = UsageTracker(this)
        pairingManager = PairingManager(this)
        fallbackStore = FallbackStateStore(this)

        startForeground(NOTIFICATION_ID, buildNotification("Sync service starting"))
        policyController.applyHardening()
        NetworkFilterController.applyBlockedPackages(this, emptySet())
        applyPoliciesAndUpload()
        startFirebaseIfConfigured()
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESCAN_APPS -> uploadAppInventory()
            ACTION_RECONCILE -> applyPoliciesAndUpload()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        valueListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        childListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        valueListeners.clear()
        childListeners.clear()
        listenersAttached = false
        super.onDestroy()
    }

    private fun startFirebaseIfConfigured() {
        if (startedFirebase) return
        val status = FirebaseBootstrap.initialize(this)
        if (!status.configured) return
        startedFirebase = true

        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { user ->
            onFirebaseReady(user)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener
                onFirebaseReady(user)
            }
            .addOnFailureListener { error ->
                recordSyncError(error.message ?: "Firebase anonymous sign-in failed")
                scheduleFirebaseRetry()
            }
    }

    private fun onFirebaseReady(user: FirebaseUser) {
        authRetryDelayMs = 5_000L
        lastSyncError = null
        db = FirebaseDatabase.getInstance().reference
        registerDevice(user.uid)
        recoverPairingStatus()
        uploadAppInventory()
        attachFirebaseListeners()
        updateHeartbeat()
    }

    private fun scheduleFirebaseRetry() {
        handler.removeCallbacks(retryFirebaseRunnable)
        handler.postDelayed(retryFirebaseRunnable, authRetryDelayMs)
        authRetryDelayMs = (authRetryDelayMs * 2).coerceAtMost(5 * 60_000L)
    }

    private fun attachFirebaseListeners() {
        if (listenersAttached) return
        listenersAttached = true
        attachPairingListener()
        attachPolicyListener()
        attachSecurityListener()
        attachCommandListener()
    }

    private fun registerValueListener(ref: DatabaseReference, listener: ValueEventListener) {
        ref.addValueEventListener(listener)
        valueListeners += ref to listener
    }

    private fun registerChildListener(ref: DatabaseReference, listener: ChildEventListener) {
        ref.addChildEventListener(listener)
        childListeners += ref to listener
    }

    private fun recordSyncError(message: String?) {
        lastSyncError = message
        db?.child(FirebasePaths.deviceSecurityRuntime(deviceId))
            ?.updateChildren(
                mapOf(
                    "lastSyncError" to message,
                    "updatedAt" to ServerValue.TIMESTAMP
                )
            )
    }

    private fun registerDevice(tvUid: String) {
        val meta = mapOf<String, Any?>(
            "deviceId" to deviceId,
            "tvUid" to tvUid,
            "label" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "androidSdk" to Build.VERSION.SDK_INT,
            "appVersion" to packageManager.getPackageInfo(packageName, 0).versionName,
            "lastRegisteredAt" to ServerValue.TIMESTAMP
        )
        db?.child(FirebasePaths.deviceMeta(deviceId))?.updateChildren(meta)
        db?.child(FirebasePaths.deviceHeartbeat(deviceId))
            ?.onDisconnect()
            ?.updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    private fun attachPairingListener() {
        val ref = db?.child(FirebasePaths.pairRequests(deviceId)) ?: return
        registerChildListener(
            ref,
            object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val parentUid = snapshot.child("parentUid").getValue(String::class.java) ?: return
                    val secret = snapshot.child("secret").getValue(String::class.java)
                    val code = snapshot.child("code").getValue(String::class.java)
                    val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                    if (!pairingManager.isValid(secret, code, createdAt)) {
                        Log.w(TAG, "Rejected pair request ${snapshot.key}: invalid secret/code")
                        snapshot.ref.child("status").setValue("rejected")
                        return
                    }

                    val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                    val updates = mapOf<String, Any?>(
                        "${FirebasePaths.deviceMeta(deviceId)}/ownerUid" to parentUid,
                        "${FirebasePaths.deviceMeta(deviceId)}/pairedAt" to ServerValue.TIMESTAMP,
                        "${FirebasePaths.userDevice(parentUid, deviceId)}/deviceId" to deviceId,
                        "${FirebasePaths.userDevice(parentUid, deviceId)}/label" to deviceLabel,
                        "${FirebasePaths.userDevice(parentUid, deviceId)}/pairedAt" to ServerValue.TIMESTAMP,
                        "${FirebasePaths.userDevice(parentUid, deviceId)}/lastSeen" to ServerValue.TIMESTAMP
                    )
                    db?.updateChildren(updates)
                    pairingManager.markPaired(parentUid)
                    Log.i(TAG, "Accepted pair request ${snapshot.key} for parent $parentUid")
                    snapshot.ref.removeValue()
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onChildRemoved(snapshot: DataSnapshot) = Unit
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onCancelled(error: DatabaseError) {
                    recordSyncError("Pairing listener cancelled: ${error.message}")
                }
            }
        )
    }

    private fun recoverPairingStatus() {
        db?.child(FirebasePaths.deviceMeta(deviceId))
            ?.child("ownerUid")
            ?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ownerUid = snapshot.getValue(String::class.java)
                    if (!ownerUid.isNullOrBlank()) {
                        pairingManager.markPaired(ownerUid)
                        Log.i(TAG, "Recovered paired parent $ownerUid from device meta")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Could not recover pairing status: ${error.message}")
                }
            })
    }

    private fun attachPolicyListener() {
        val ref = db?.child(FirebasePaths.devicePolicyApps(deviceId)) ?: return
        registerValueListener(
            ref,
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val policies = snapshot.children.mapNotNull { child ->
                        val packageName = child.child("packageName").getValue(String::class.java)
                            ?: runCatching { PackageKeys.decode(child.key.orEmpty()) }.getOrNull()
                            ?: return@mapNotNull null
                        val manualBlocked = child.child("manualBlocked").getValue(Boolean::class.java) ?: false
                        val limit = child.child("dailyLimitMinutes").getValue(Long::class.java)
                            ?.toInt()
                            ?.takeIf { it > 0 }
                        packageName to AppPolicy(manualBlocked, limit)
                    }.toMap()
                    localPolicyStore.savePolicies(policies)
                    applyPoliciesAndUpload()
                }

                override fun onCancelled(error: DatabaseError) {
                    recordSyncError("Policy listener cancelled: ${error.message}")
                }
            }
        )
    }

    private fun attachSecurityListener() {
        val ref = db?.child(FirebasePaths.deviceSecurityPin(deviceId)) ?: return
        registerValueListener(
            ref,
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val salt = snapshot.child("salt").getValue(String::class.java)
                    val hash = snapshot.child("hash").getValue(String::class.java)
                    if (salt.isNullOrBlank() || hash.isNullOrBlank()) {
                        fallbackStore.savePin(null)
                    } else {
                        fallbackStore.savePin(
                            PinRecord(
                                salt = salt,
                                hash = hash,
                                updatedAt = snapshot.child("updatedAt").getValue(Long::class.java) ?: 0L
                            )
                        )
                    }
                    updateSecurityRuntime()
                }

                override fun onCancelled(error: DatabaseError) {
                    recordSyncError("Security listener cancelled: ${error.message}")
                }
            }
        )
    }

    private fun attachCommandListener() {
        val ref = db?.child(FirebasePaths.deviceCommands(deviceId)) ?: return
        registerChildListener(
            ref,
            object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    if (snapshot.child("status").exists()) return
                    val type = snapshot.child("type").getValue(String::class.java) ?: return
                    snapshot.ref.updateChildren(mapOf("status" to "running", "startedAt" to ServerValue.TIMESTAMP))
                    runCatching {
                        when (type) {
                            PolicyConstants.COMMAND_RESCAN_APPS -> uploadAppInventory()
                            PolicyConstants.COMMAND_RESET_TODAY -> {
                                val packageName = snapshot.child("packageName").getValue(String::class.java)
                                val currentUsage = usageTracker.usageMinutesToday()
                                if (packageName == null) {
                                    localPolicyStore.clearDailyLimitBlocks()
                                    currentUsage.forEach { (pkg, minutes) ->
                                        localPolicyStore.saveUsageOffset(pkg, minutes)
                                    }
                                } else {
                                    localPolicyStore.clearDailyLimitBlocks(packageName)
                                    localPolicyStore.saveUsageOffset(packageName, currentUsage[packageName] ?: 0L)
                                }
                                applyPoliciesAndUpload()
                            }
                            PolicyConstants.COMMAND_UNPAIR -> {
                                val parentUid = pairingManager.pairedParentUid()
                                pairingManager.clearPairedParent()
                                val updates = mutableMapOf<String, Any?>(
                                    "${FirebasePaths.deviceMeta(deviceId)}/ownerUid" to null,
                                    "${FirebasePaths.deviceMeta(deviceId)}/pairedAt" to null
                                )
                                if (!parentUid.isNullOrBlank()) {
                                    updates[FirebasePaths.userDevice(parentUid, deviceId)] = null
                                }
                                db?.updateChildren(updates)
                            }
                            PolicyConstants.COMMAND_OPEN_SETUP -> openSetup()
                            else -> error("Unknown command type: $type")
                        }
                    }.onSuccess {
                        snapshot.ref.updateChildren(
                            mapOf("status" to "done", "completedAt" to ServerValue.TIMESTAMP)
                        )
                    }.onFailure { error ->
                        snapshot.ref.updateChildren(
                            mapOf(
                                "status" to "failed",
                                "error" to error.message,
                                "completedAt" to ServerValue.TIMESTAMP
                            )
                        )
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onChildRemoved(snapshot: DataSnapshot) = Unit
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onCancelled(error: DatabaseError) {
                    recordSyncError("Command listener cancelled: ${error.message}")
                }
            }
        )
    }

    private fun uploadAppInventory() {
        val apps = inventoryProvider.listLaunchableApps()
        val payload = apps.associate { PackageKeys.encode(it.packageName) to it.toFirebaseMap() }
        db?.child(FirebasePaths.deviceApps(deviceId))?.setValue(payload)
        applyPoliciesAndUpload(apps)
    }

    private fun updateHeartbeat() {
        val mode = FallbackProtection.enforcementMode(this)
        val adminSetupAvailable = FallbackProtection.isDeviceAdminSetupAvailable(this)
        val accessibility = FallbackProtection.isAccessibilityEnabled(this)
        val usageAccess = usageTracker.hasUsageAccess()
        val vpnStatus = NetworkFilterController.refreshPreparedStatus(this)
        val backgroundUnrestricted = BackgroundRestrictionStatus.isBatteryUnrestricted(this)
        val pinConfigured = fallbackStore.loadPin() != null
        val protectionHealthy = mode == PolicyConstants.ENFORCEMENT_DEVICE_OWNER ||
            ((!adminSetupAvailable || policyController.isAdminActive()) &&
                accessibility &&
                usageAccess &&
                backgroundUnrestricted &&
                pinConfigured)

        val heartbeat = mapOf(
            "online" to true,
            "lastSeen" to ServerValue.TIMESTAMP,
            "usageAccess" to usageAccess,
            "vpnActive" to vpnStatus.active,
            "vpnBlockedCount" to vpnStatus.blockedCount,
            "backgroundUnrestricted" to backgroundUnrestricted,
            "deviceOwner" to policyController.isDeviceOwner(),
            "enforcementMode" to mode,
            "protectionHealthy" to protectionHealthy
        )
        db?.child(FirebasePaths.deviceHeartbeat(deviceId))?.updateChildren(heartbeat)
        pairingManager.pairedParentUid()?.let { parentUid ->
            db?.child(FirebasePaths.userDevice(parentUid, deviceId))?.updateChildren(
                mapOf(
                    "deviceId" to deviceId,
                    "lastSeen" to ServerValue.TIMESTAMP,
                    "online" to true,
                    "enforcementMode" to mode,
                    "protectionHealthy" to protectionHealthy
                )
            )
            db?.child(FirebasePaths.userDevice(parentUid, deviceId))
                ?.onDisconnect()
                ?.updateChildren(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
        }
        updateSecurityRuntime()
    }

    private fun updateSecurityRuntime() {
        val adminActive = policyController.isAdminActive()
        val adminSetupAvailable = FallbackProtection.isDeviceAdminSetupAvailable(this)
        val accessibility = FallbackProtection.isAccessibilityEnabled(this)
        val usage = usageTracker.hasUsageAccess()
        val mode = FallbackProtection.enforcementMode(this)
        val vpnStatus = NetworkFilterController.refreshPreparedStatus(this)
        val backgroundUnrestricted = BackgroundRestrictionStatus.isBatteryUnrestricted(this)
        db?.child(FirebasePaths.deviceSecurityRuntime(deviceId))?.updateChildren(
            mapOf(
                "enforcementMode" to mode,
                "deviceOwner" to policyController.isDeviceOwner(),
                "deviceAdmin" to adminActive,
                "deviceAdminSetupAvailable" to adminSetupAvailable,
                "accessibility" to accessibility,
                "usageAccess" to usage,
                "vpnPrepared" to vpnStatus.prepared,
                "vpnActive" to vpnStatus.active,
                "vpnBlockedCount" to vpnStatus.blockedCount,
                "vpnLastError" to vpnStatus.lastError,
                "backgroundUnrestricted" to backgroundUnrestricted,
                "pinConfigured" to (fallbackStore.loadPin() != null),
                "protectionHealthy" to (
                    mode == PolicyConstants.ENFORCEMENT_DEVICE_OWNER ||
                        ((adminActive || !adminSetupAvailable) &&
                            accessibility &&
                            usage &&
                            backgroundUnrestricted &&
                            fallbackStore.loadPin() != null)
                    ),
                "lastForegroundPackage" to fallbackStore.lastForeground(),
                "lastSyncError" to lastSyncError,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )
        uploadMissingProtectionEvents(adminActive, adminSetupAvailable, accessibility, usage, vpnStatus)
    }

    private fun uploadMissingProtectionEvents(
        adminActive: Boolean,
        adminSetupAvailable: Boolean,
        accessibility: Boolean,
        usageAccess: Boolean,
        @Suppress("UNUSED_PARAMETER") vpnStatus: com.guardpulse.parentcontrol.tv.network.NetworkFilterStatus
    ) {
        if (policyController.isDeviceOwner()) return
        val missing = mutableListOf<String>()
        if (adminSetupAvailable && !adminActive) missing += PolicyConstants.TAMPER_ADMIN_DISABLED
        if (!accessibility) missing += PolicyConstants.TAMPER_ACCESSIBILITY_DISABLED
        if (!usageAccess) missing += PolicyConstants.TAMPER_USAGE_ACCESS_MISSING
        missing.forEach { type ->
            if (!fallbackStore.shouldReportTamper(type)) return@forEach
            db?.child(FirebasePaths.deviceTamperEvents(deviceId))?.push()?.setValue(
                mapOf(
                    "type" to type,
                    "createdAt" to ServerValue.TIMESTAMP,
                    "message" to "Fallback protection is incomplete: $type"
                )
            )
        }
    }

    private fun applyPoliciesAndUpload(apps: List<TvInstalledApp> = inventoryProvider.listLaunchableApps()) {
        val policies = localPolicyStore.loadPolicies()
        val usage = usageTracker.usageMinutesToday()
        val usageOffsets = localPolicyStore.loadUsageOffsets()
        val dailyBlocks = localPolicyStore.loadDailyLimitBlocks().toMutableSet()
        val states = mutableMapOf<String, Any?>()
        val enforcementMode = FallbackProtection.enforcementMode(this)
        val decisionsByPackage = mutableMapOf<String, com.guardpulse.parentcontrol.shared.PolicyDecision>()
        val usageByPackage = mutableMapOf<String, Long>()
        val rawUsageByPackage = mutableMapOf<String, Long>()
        val networkBlockedPackages = emptySet<String>()

        apps.forEach { app ->
            val policy = effectivePolicy(app.packageName, policies)
            val rawUsageMinutes = usage[app.packageName] ?: 0L
            val usageMinutes = (rawUsageMinutes - (usageOffsets[app.packageName] ?: 0L)).coerceAtLeast(0L)
            val limit = policy.dailyLimitMinutes
            if (limit != null && usageMinutes >= limit) {
                localPolicyStore.markDailyLimitBlocked(app.packageName)
                dailyBlocks.add(app.packageName)
            }

            val decision = PolicyDecider.decide(
                policy = DesiredAppPolicy(
                    packageName = app.packageName,
                    manualBlocked = policy.manualBlocked,
                    dailyLimitMinutes = policy.dailyLimitMinutes
                ),
                usageMinutesToday = usageMinutes,
                alreadyDailyBlocked = app.packageName in dailyBlocks
            )
            if (decision.dailyLimitBlocked) {
                dailyBlocks.add(app.packageName)
            }
            decisionsByPackage[app.packageName] = decision
            usageByPackage[app.packageName] = usageMinutes
            rawUsageByPackage[app.packageName] = rawUsageMinutes

        }

        val vpnStatus = NetworkFilterController.applyBlockedPackages(this, networkBlockedPackages)

        apps.forEach { app ->
            val policy = effectivePolicy(app.packageName, policies)
            val decision = decisionsByPackage[app.packageName]
                ?: com.guardpulse.parentcontrol.shared.PolicyDecision(false, null, false, false)
            val usageMinutes = usageByPackage[app.packageName] ?: 0L
            val rawUsageMinutes = rawUsageByPackage[app.packageName] ?: 0L
            val limit = policy.dailyLimitMinutes
            val networkBlocked = app.packageName in networkBlockedPackages
            val vpnApplied = networkBlocked && vpnStatus.active
            val sourceLocked = decision.shouldBlock && app.packageName in PolicyConstants.sourceLockPackages
            val settingsSectionLocked = decision.shouldBlock &&
                app.packageName in PolicyConstants.settingsSectionLockPackages
            val primarySettingsLocked = decision.shouldBlock && app.packageName in PolicyConstants.primarySettingsPackages
            val riskySettingsLocked = primarySettingsLocked ||
                (app.packageName in PolicyConstants.riskySettingsPackages &&
                    app.packageName !in PolicyConstants.primarySettingsPackages)
            val normalAppLocked = decision.shouldBlock &&
                app.blockable &&
                app.packageName !in PolicyConstants.virtualPolicyPackages &&
                app.packageName !in PolicyConstants.sourceLockPackages &&
                app.packageName !in PolicyConstants.primarySettingsPackages &&
                app.packageName != packageName
            val lockBlocked = riskySettingsLocked || sourceLocked || settingsSectionLocked || normalAppLocked
            val lockReason = when {
                sourceLocked -> PolicyConstants.BLOCK_REASON_SOURCE_LOCK
                settingsSectionLocked -> PolicyConstants.BLOCK_REASON_SETTINGS_SECTION
                riskySettingsLocked -> PolicyConstants.BLOCK_REASON_RISKY_SETTINGS
                normalAppLocked -> decision.reason ?: PolicyConstants.BLOCK_REASON_MANUAL
                else -> null
            }
            var lastError: String? = null
            if (decision.shouldBlock && !app.blockable) {
                lastError = "Protected package: ${app.protectedReason}"
            }

            states[PackageKeys.encode(app.packageName)] = mapOf(
                "packageName" to app.packageName,
                "suspended" to policyController.isSuspended(app.packageName),
                "requestedSuspended" to false,
                "manualBlocked" to decision.manualBlocked,
                "dailyLimitBlocked" to decision.dailyLimitBlocked,
                "networkBlocked" to networkBlocked,
                "vpnApplied" to vpnApplied,
                "vpnActive" to vpnStatus.active,
                "lockBlocked" to lockBlocked,
                "lockReason" to lockReason,
                "vpnLastError" to null,
                "blockReason" to decision.reason,
                "enforcementMode" to enforcementMode,
                "fallbackLocked" to lockBlocked,
                "usageMinutesToday" to usageMinutes,
                "rawUsageMinutesToday" to rawUsageMinutes,
                "dailyLimitMinutes" to limit,
                "blockable" to app.blockable,
                "lastError" to lastError,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        }
        PolicyConstants.deprecatedVirtualPolicyPackages.forEach { packageName ->
            states[PackageKeys.encode(packageName)] = null
        }

        db?.child(FirebasePaths.deviceStateApps(deviceId))?.updateChildren(states)
        enforceCurrentForegroundLock(policies, dailyBlocks)
    }

    private fun effectivePolicy(
        packageName: String,
        policies: Map<String, AppPolicy>
    ): AppPolicy {
        return policies[packageName] ?: if (PolicyConstants.isDefaultLocked(packageName)) {
            AppPolicy(manualBlocked = true)
        } else {
            AppPolicy()
        }
    }

    private fun enforceCurrentForegroundLock(
        policies: Map<String, AppPolicy>,
        dailyBlocks: Set<String>
    ) {
        val foregroundPackage = fallbackStore.lastForeground() ?: return
        val decision = FallbackProtection.shouldLock(
            context = this,
            foregroundPackage = foregroundPackage,
            policies = policies,
            dailyBlocks = dailyBlocks,
            fallbackStore = fallbackStore
        )
        if (!decision.locked) return
        FallbackProtection.openLock(
            this,
            decision.policyPackage ?: foregroundPackage,
            decision.reason ?: PolicyConstants.BLOCK_REASON_MANUAL
        )
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            policyController.applyHardening()
            applyPoliciesAndUpload()
            updateHeartbeat()
            handler.postDelayed(this, PolicyConstants.HEARTBEAT_INTERVAL_MS)
        }
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device service sync",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_control)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GuardPulseTvSync"
        const val ACTION_RESCAN_APPS = "com.guardpulse.parentcontrol.tv.action.RESCAN_APPS"
        const val ACTION_RECONCILE = "com.guardpulse.parentcontrol.tv.action.RECONCILE"
        private const val CHANNEL_ID = "tv_parental_control"
        private const val NOTIFICATION_ID = 1001
    }

    private fun openSetup() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}
