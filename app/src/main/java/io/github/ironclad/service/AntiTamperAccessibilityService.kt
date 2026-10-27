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
 * Intercepts user attempts to open Settings, uninstall the app, or disconnect
 * the VPN while protection is locked, immediately ejecting back to the home launcher.
 */
class AntiTamperAccessibilityService : AccessibilityService() {

    private lateinit var timer: QuarantineTimer
    private var lastKickTime = 0L
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

        // Intercept target Settings / Installer packages
        if (pkg.contains("settings", ignoreCase = true) ||
            pkg.contains("packageinstaller", ignoreCase = true) ||
            pkg.contains("google.android.apps.safetyhub", ignoreCase = true)
        ) {
            val rootNode = rootInActiveWindow ?: return
            if (isTamperingAttempt(rootNode)) {
                ejectToHome()
            }
        }
    }

    private fun isTamperingAttempt(node: AccessibilityNodeInfo): Boolean {
        // Check for indicators of app management or network disconnection
        val triggers = listOf(
            "Ironclad",
            "Uninstall",
            "Force stop",
            "Disconnect",
            "Forget VPN",
            "Private DNS",
            "Turn off",
            "Always-on VPN"
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
        val now = System.currentTimeMillis()
        if (now - lastKickTime < 1500) return // Throttle toasts and actions
        lastKickTime = now

        performGlobalAction(GLOBAL_ACTION_HOME)

        mainHandler.post {
            Toast.makeText(
                applicationContext,
                "🛡️ Ironclad Protection Active: Initiate 24h unlock in Ironclad to access settings.",
                Toast.LENGTH_LONG
            ).show()
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
