package com.guardpulse.parentcontrol.tv.fallback

import com.guardpulse.parentcontrol.shared.PolicyConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSectionDetectorTest {
    @Test
    fun topLevelSettingsRowsStayAllowedExceptApps() {
        val topLevel = """
            Settings
            General Settings
            Network & Internet
            Inputs
            Accounts & Sign In
            Apps
            Device Preferences
        """.trimIndent()

        assertNull(detect(focusedText = "Network & Internet", windowText = topLevel))
        assertNull(detect(focusedText = "Inputs", windowText = topLevel))
        assertNull(detect(focusedText = "Accounts & Sign In", windowText = topLevel))
        assertNull(detect(focusedText = "Device Preferences", windowText = topLevel))

        assertEquals(
            PolicyConstants.SETTINGS_APPS_PACKAGE,
            detect(focusedText = "Apps", windowText = topLevel)?.policyPackage
        )
    }

    @Test
    fun devicePreferencesLocksOnlyRequestedRows() {
        val devicePreferences = """
            Device Preferences
            Home screen
            Google Assistant
            Chromecast built-in
            Screen saver
            Developer options
            Location
            Usage & Diagnostics
            Security & restrictions
            Accessibility
            TV lock
            Reset
        """.trimIndent()

        assertNull(detect(focusedText = "", windowText = devicePreferences))
        assertNull(detect(focusedText = "Home screen", windowText = devicePreferences))
        assertNull(detect(focusedText = "Google Assistant", windowText = devicePreferences))
        assertNull(detect(focusedText = "Chromecast built-in", windowText = devicePreferences))
        assertNull(detect(focusedText = "Screen saver", windowText = devicePreferences))
        assertNull(detect(focusedText = "Location", windowText = devicePreferences))
        assertNull(detect(focusedText = "Usage & Diagnostics", windowText = devicePreferences))
        assertNull(detect(focusedText = "TV lock", windowText = devicePreferences))

        assertEquals(
            PolicyConstants.SETTINGS_DEVELOPER_OPTIONS_PACKAGE,
            detect(focusedText = "Developer options", windowText = devicePreferences)?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_SECURITY_RESTRICTIONS_PACKAGE,
            detect(focusedText = "Security & restrictions", windowText = devicePreferences)?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_ACCESSIBILITY_PACKAGE,
            detect(focusedText = "Accessibility", windowText = devicePreferences)?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_RESET_PACKAGE,
            detect(focusedText = "Reset", windowText = devicePreferences)?.policyPackage
        )
    }

    @Test
    fun subpagesLockOnlyRequestedAreas() {
        assertEquals(
            PolicyConstants.SETTINGS_APPS_PACKAGE,
            detect(windowText = "App info\nYouTube\nForce stop\nUninstall")?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_DEVELOPER_OPTIONS_PACKAGE,
            detect(windowText = "Developer options\nUSB debugging")?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_SECURITY_RESTRICTIONS_PACKAGE,
            detect(windowText = "Security & restrictions\nUnknown sources")?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_ACCESSIBILITY_PACKAGE,
            detect(windowText = "Accessibility\nDevice Support Service")?.policyPackage
        )
        assertEquals(
            PolicyConstants.SETTINGS_RESET_PACKAGE,
            detect(windowText = "Reset\nFactory data reset")?.policyPackage
        )

        assertNull(detect(windowText = "VPN\nGuardPulse Network Service"))
        assertNull(detect(windowText = "Usage access\nDevice Service"))
        assertNull(detect(windowText = "Permission manager\nCamera"))
    }

    private fun detect(
        packageName: String = "com.android.tv.settings",
        focusedText: String = "",
        eventText: String = "",
        windowText: String = ""
    ): ProtectedSettingsSection? {
        return SettingsSectionDetector.detectFromText(
            packageName = packageName,
            focusedText = focusedText,
            eventText = eventText,
            windowText = windowText
        )
    }
}
