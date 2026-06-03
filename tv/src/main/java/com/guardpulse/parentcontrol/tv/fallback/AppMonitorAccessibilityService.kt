package com.guardpulse.parentcontrol.tv.fallback

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.guardpulse.parentcontrol.shared.DeviceIdentity
import com.guardpulse.parentcontrol.shared.FirebaseBootstrap
import com.guardpulse.parentcontrol.shared.FirebasePaths
import com.guardpulse.parentcontrol.shared.PolicyConstants
import com.guardpulse.parentcontrol.tv.policy.LocalPolicyStore
import com.guardpulse.parentcontrol.tv.system.TvServiceStarter

class AppMonitorAccessibilityService : AccessibilityService() {
    private lateinit var localPolicyStore: LocalPolicyStore
    private lateinit var fallbackStore: FallbackStateStore
    private var lastLockedKey: String? = null
    private var lastLockAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val policyChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "policies" ||
            key?.startsWith("dailyBlocks:") == true ||
            key?.startsWith("usageOffsets:") == true
        ) {
            mainHandler.post { evaluateCurrentWindow() }
        }
    }

    override fun onServiceConnected() {
        localPolicyStore = LocalPolicyStore(this)
        fallbackStore = FallbackStateStore(this)
        localPolicyStore.registerChangeListener(policyChangeListener)
        runCatching { TvServiceStarter.start(this) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (!::localPolicyStore.isInitialized) return
        evaluateForeground(
            packageName = packageName,
            eventClassName = event.className,
            eventText = event.text,
            root = rootInActiveWindow
        )
    }

    private fun evaluateCurrentWindow() {
        if (!::localPolicyStore.isInitialized) return
        val root = rootInActiveWindow
        val packageName = root?.packageName?.toString()
            ?: fallbackStore.lastForeground()
            ?: return
        evaluateForeground(
            packageName = packageName,
            eventClassName = root?.className,
            eventText = emptyList(),
            root = root
        )
    }

    private fun evaluateForeground(
        packageName: String,
        eventClassName: CharSequence?,
        eventText: List<CharSequence>,
        root: AccessibilityNodeInfo?
    ) {
        fallbackStore.saveLastForeground(packageName)
        clearSetupVisitUnlockIfLeft(packageName)
        clearAppVisitUnlockIfLeft(packageName)
        val settingsSection = SettingsSectionDetector.detect(
            packageName = packageName,
            eventClassName = eventClassName,
            eventText = eventText,
            root = root
        )
        if (settingsSection == null && packageName in PolicyConstants.primarySettingsPackages) {
            fallbackStore.clearSettingsSectionUnlock()
        }
        if (packageName !in PolicyConstants.primarySettingsPackages && packageName != this.packageName) {
            fallbackStore.clearSettingsSectionUnlock()
        }

        val decision = FallbackProtection.shouldLock(
            context = this,
            foregroundPackage = packageName,
            policies = localPolicyStore.loadPolicies(),
            dailyBlocks = localPolicyStore.loadDailyLimitBlocks(),
            fallbackStore = fallbackStore,
            settingsSection = settingsSection
        )
        if (!decision.locked) return

        val now = System.currentTimeMillis()
        val lockKey = listOfNotNull(packageName, decision.reason, decision.settingsSectionKey).joinToString(":")
        if (lastLockedKey == lockKey && now - lastLockAt < 1_500L) return
        lastLockedKey = lockKey
        lastLockAt = now
        if (decision.reason == PolicyConstants.BLOCK_REASON_RISKY_SETTINGS ||
            decision.reason == PolicyConstants.BLOCK_REASON_SETTINGS_SECTION
        ) {
            uploadRiskySettingsEvent(packageName)
        }
        FallbackProtection.openLock(
            this,
            decision.policyPackage ?: packageName,
            decision.reason ?: PolicyConstants.BLOCK_REASON_MANUAL,
            decision.settingsSectionKey
        )
    }

    private fun clearAppVisitUnlockIfLeft(packageName: String) {
        val unlockedPolicyPackage = fallbackStore.appVisitUnlockPackage() ?: return
        if (packageName == this.packageName) return
        val currentPolicyPackage = PolicyConstants.sourceLockPolicyPackage(packageName) ?: packageName
        if (currentPolicyPackage != unlockedPolicyPackage) {
            fallbackStore.clearAppVisitUnlock()
        }
    }

    private fun clearSetupVisitUnlockIfLeft(packageName: String) {
        if (packageName != this.packageName) {
            fallbackStore.clearSetupVisitUnlock()
        }
    }

    override fun onDestroy() {
        if (::localPolicyStore.isInitialized) {
            localPolicyStore.unregisterChangeListener(policyChangeListener)
        }
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun uploadRiskySettingsEvent(packageName: String) {
        if (!fallbackStore.shouldReportTamper(PolicyConstants.TAMPER_RISKY_SETTINGS_OPENED)) return
        val status = FirebaseBootstrap.initialize(applicationContext)
        if (!status.configured) return
        val writeEvent = {
            val deviceId = DeviceIdentity.getOrCreate(applicationContext)
            FirebaseDatabase.getInstance().reference
                .child(FirebasePaths.deviceTamperEvents(deviceId))
                .push()
                .setValue(
                    mapOf(
                        "type" to PolicyConstants.TAMPER_RISKY_SETTINGS_OPENED,
                        "createdAt" to ServerValue.TIMESTAMP,
                        "message" to "Protected settings opened: $packageName"
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

    override fun onInterrupt() = Unit
}
