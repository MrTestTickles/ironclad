package io.github.ironclad.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import io.github.ironclad.admin.IronDeviceAdminReceiver
import io.github.ironclad.gauntlet.QuarantineTimer

/**
 * Resilient boot receiver that restores protection automatically upon device reboot
 * or app update, eliminating the "reboot device to bypass" loophole.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IroncladBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        Log.i(TAG, "Boot or package replaced detected ($action) — verifying protection state")

        // 1. Re-enforce Device Owner restrictions immediately
        if (IronDeviceAdminReceiver.isDeviceOwner(context)) {
            IronDeviceAdminReceiver.enforceDefaultPolicies(context)
            IronDeviceAdminReceiver.enforcePrivateDns(context)
        }

        // 2. Restart Ironclad VPN if active
        val timer = QuarantineTimer(context)
        if (timer.getState() != QuarantineTimer.State.GRACE_UNLOCKED) {
            val prefs = context.getSharedPreferences(IronVpnService.PREFS_NAME, Context.MODE_PRIVATE)
            val wasVpnActive = prefs.getBoolean(IronVpnService.KEY_VPN_ACTIVE, false)

            if (wasVpnActive || IronDeviceAdminReceiver.isDeviceOwner(context)) {
                // Check if VPN permission has already been granted by user / OS
                if (VpnService.prepare(context) == null) {
                    val vpnIntent = Intent(context, IronVpnService::class.java).apply {
                        this.action = IronVpnService.ACTION_START
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(vpnIntent)
                        } else {
                            context.startService(vpnIntent)
                        }
                        Log.i(TAG, "IronVpnService auto-started successfully on boot")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start IronVpnService on boot", e)
                    }
                }
            }
        }
    }
}
