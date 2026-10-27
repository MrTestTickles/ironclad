package io.github.ironclad.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import io.github.ironclad.gauntlet.QuarantineTimer

/**
 * Tier 1 Anti-Tamper Shield (No ADB required).
 * Intercepts user attempts to open Settings, uninstall the app via Settings or Play Store,
 * disconnect the VPN in Quick Settings, or tamper with device admin / accessibility while
 * protection is locked, immediately ejecting back to the home launcher.
 */
class AntiTamperAccessibilityService : AccessibilityService() {

    private lateinit var timer: QuarantineTimer
    private var lastToastTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        timer = QuarantineTimer(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::timer.isInitialized) return

        // If the 24h quarantine and gauntlet were passed, allow user into settings
        if (timer.getState() == QuarantineTimer.State.GRACE_UNLOCKED) return

        val pkg = event.packageName?.toString() ?: return
        val rootNode = rootInActiveWindow ?: return

        // 1. Google Play Store evasion prevention (uninstalling Ironclad via Play Store)
        if (pkg.contains("vending", ignoreCase = true)) {
            if (isPlayStoreTampering(rootNode)) {
                ejectToHome()
            }
            return
        }

        // 2. Notification Shade / Quick Settings VPN disconnect evasion
        if (pkg.contains("systemui", ignoreCase = true)) {
            if (isSystemUiVpnTampering(rootNode)) {
                ejectToHome()
            }
            return
        }

        // 3. Settings, PackageInstaller, and Security Hub tampering
        if (pkg.contains("settings", ignoreCase = true) ||
            pkg.contains("packageinstaller", ignoreCase = true) ||
            pkg.contains("google.android.apps.safetyhub", ignoreCase = true)
        ) {
            if (isTamperingAttempt(rootNode)) {
                ejectToHome()
            }
        }
    }

    private fun isPlayStoreTampering(node: AccessibilityNodeInfo): Boolean {
        val hasIronclad = node.findAccessibilityNodeInfosByText("Ironclad").isNotEmpty()
        if (!hasIronclad) return false

        val uninstallTriggers = listOf("Uninstall", "Installed", "Open", "Cancel")
        for (trigger in uninstallTriggers) {
            if (node.findAccessibilityNodeInfosByText(trigger).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun isSystemUiVpnTampering(node: AccessibilityNodeInfo): Boolean {
        val vpnTriggers = listOf("Disconnect", "Forget VPN", "Turn off", "Pause")
        for (trigger in vpnTriggers) {
            val matches = node.findAccessibilityNodeInfosByText(trigger)
            if (matches.isNotEmpty()) {
                val hasVpnOrIronclad = node.findAccessibilityNodeInfosByText("VPN").isNotEmpty() ||
                        node.findAccessibilityNodeInfosByText("Ironclad").isNotEmpty()
                if (hasVpnOrIronclad) return true
            }
        }
        return false
    }

    private fun isTamperingAttempt(node: AccessibilityNodeInfo): Boolean {
        val triggers = listOf(
            "Ironclad",
            "Uninstall",
            "Force stop",
            "Disconnect",
            "Forget VPN",
            "Private DNS",
            "Turn off",
            "Always-on VPN",
            "Clear storage",
            "Clear data",
            "Delete data",
            "Storage & cache",
            "Deactivate",
            "Disable",
            "Special app access",
            "Device admin apps",
            "Device admin"
        )

        for (trigger in triggers) {
            val matches = node.findAccessibilityNodeInfosByText(trigger)
            if (matches.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun ejectToHome() {
        // Always immediately eject to prevent race conditions
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)

        val now = System.currentTimeMillis()
        if (now - lastToastTime > 2000) {
            lastToastTime = now
            mainHandler.post {
                Toast.makeText(
                    applicationContext,
                    "🛡️ Ironclad Protection Active: Initiate 24h unlock in Ironclad to access settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onInterrupt() {}

    companion object {
        fun isServiceRunning(context: Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(context.packageName)
        }
    }
}
