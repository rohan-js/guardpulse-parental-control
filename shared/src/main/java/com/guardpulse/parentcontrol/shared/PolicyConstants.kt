package com.guardpulse.parentcontrol.shared

object PolicyConstants {
    data class SettingsSectionPolicy(
        val packageName: String,
        val label: String,
        val shortLabel: String,
        val key: String
    )

    const val TV_PACKAGE = "com.guardpulse.parentcontrol.tv"
    const val PARENT_PACKAGE = "com.guardpulse.parentcontrol.parent"
    const val SETTINGS_APPS_PACKAGE = "com.guardpulse.policy.settings_apps"
    const val SETTINGS_DEVELOPER_OPTIONS_PACKAGE = "com.guardpulse.policy.settings_developer_options"
    const val SETTINGS_SECURITY_RESTRICTIONS_PACKAGE = "com.guardpulse.policy.settings_security_restrictions"
    const val SETTINGS_ACCESSIBILITY_PACKAGE = "com.guardpulse.policy.settings_accessibility"
    const val SETTINGS_RESET_PACKAGE = "com.guardpulse.policy.settings_reset"
    const val DEPRECATED_SETTINGS_SECTIONS_PACKAGE = "com.guardpulse.policy.settings_sections"

    const val COMMAND_RESCAN_APPS = "rescanApps"
    const val COMMAND_RESET_TODAY = "resetToday"
    const val COMMAND_UNPAIR = "unpair"
    const val COMMAND_OPEN_SETUP = "openSetup"

    const val BLOCK_REASON_MANUAL = "manual"
    const val BLOCK_REASON_DAILY_LIMIT = "dailyLimit"
    const val BLOCK_REASON_RISKY_SETTINGS = "riskySettings"
    const val BLOCK_REASON_NETWORK_FILTER_MISSING = "networkFilterMissing"
    const val BLOCK_REASON_SOURCE_LOCK = "sourceLock"
    const val BLOCK_REASON_SETTINGS_SECTION = "settingsSection"

    const val ENFORCEMENT_DEVICE_OWNER = "deviceOwner"
    const val ENFORCEMENT_FALLBACK = "fallback"
    const val ENFORCEMENT_UNPROTECTED = "unprotected"

    const val UNLOCK_PENDING = "pending"
    const val UNLOCK_APPROVED = "approved"
    const val UNLOCK_DENIED = "denied"
    const val UNLOCK_EXPIRED = "expired"

    const val TAMPER_ADMIN_DISABLE_REQUESTED = "adminDisableRequested"
    const val TAMPER_ADMIN_DISABLED = "adminDisabled"
    const val TAMPER_ACCESSIBILITY_DISABLED = "accessibilityDisabled"
    const val TAMPER_USAGE_ACCESS_MISSING = "usageAccessMissing"
    const val TAMPER_VPN_DISABLED = "vpnDisabled"
    const val TAMPER_RISKY_SETTINGS_OPENED = "riskySettingsOpened"

    const val HEARTBEAT_INTERVAL_MS = 60_000L
    const val USAGE_SCAN_INTERVAL_MS = 60_000L
    const val PAIRING_TTL_MS = 10 * 60_000L
    const val TEMP_UNLOCK_MS = 10 * 60_000L
    const val TAMPER_EVENT_THROTTLE_MS = 15 * 60_000L

    val sourceLockPackages = setOf(
        "com.android.tv"
    )

    val sourceLockRuntimePackages = sourceLockPackages + setOf(
        "com.droidlogic.tvinput"
    )

    val primarySettingsPackages = setOf(
        "com.android.tv.settings",
        "com.android.settings"
    )

    val settingsSectionPolicies = listOf(
        SettingsSectionPolicy(
            packageName = SETTINGS_APPS_PACKAGE,
            label = "Settings: Apps",
            shortLabel = "Apps",
            key = "settings-apps"
        ),
        SettingsSectionPolicy(
            packageName = SETTINGS_DEVELOPER_OPTIONS_PACKAGE,
            label = "Settings: Developer options",
            shortLabel = "Developer options",
            key = "settings-developer-options"
        ),
        SettingsSectionPolicy(
            packageName = SETTINGS_SECURITY_RESTRICTIONS_PACKAGE,
            label = "Settings: Security & restrictions",
            shortLabel = "Security & restrictions",
            key = "settings-security-restrictions"
        ),
        SettingsSectionPolicy(
            packageName = SETTINGS_ACCESSIBILITY_PACKAGE,
            label = "Settings: Accessibility",
            shortLabel = "Accessibility",
            key = "settings-accessibility"
        ),
        SettingsSectionPolicy(
            packageName = SETTINGS_RESET_PACKAGE,
            label = "Settings: Reset",
            shortLabel = "Reset",
            key = "settings-reset"
        )
    )

    val settingsSectionLockPackages = settingsSectionPolicies.map { it.packageName }.toSet()

    val virtualPolicyPackages = settingsSectionLockPackages

    val deprecatedVirtualPolicyPackages = setOf(
        DEPRECATED_SETTINGS_SECTIONS_PACKAGE
    )

    val parentVisibleLockPackages = sourceLockPackages + primarySettingsPackages + settingsSectionLockPackages

    val defaultLockedPackages = settingsSectionLockPackages

    val alwaysProtectedPackages = setOf(
        TV_PACKAGE,
        "com.android.systemui",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.documentsui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx"
    )

    val riskySettingsPackages = emptySet<String>()

    fun isDefaultLocked(packageName: String): Boolean = packageName in defaultLockedPackages

    fun settingsSectionPolicy(packageName: String): SettingsSectionPolicy? {
        return settingsSectionPolicies.firstOrNull { it.packageName == packageName }
    }

    fun settingsSectionPolicyForKey(key: String): SettingsSectionPolicy? {
        return settingsSectionPolicies.firstOrNull { it.key == key }
    }

    fun sourceLockPolicyPackage(packageName: String): String? {
        return if (packageName in sourceLockRuntimePackages) sourceLockPackages.first() else null
    }
}
