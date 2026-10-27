package io.github.ironclad.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast

class IronDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Ironclad Device Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        enforceDefaultPolicies(context)
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, IronDeviceAdminReceiver::class.java)
        }

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        /**
         * Enforces foundational enterprise tamper restrictions.
         */
        fun enforceDefaultPolicies(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)

            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            // 1. Prevent uninstallation of Ironclad
            dpm.setUninstallBlocked(admin, context.packageName, true)

            // 2. Disable Safe Mode rebooting
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)

            // 3. Prevent creating guest or secondary profiles
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

            // 4. Prevent factory resets without password
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        }

        /**
         * Tier 2 Mode A: Enforce system Private DNS (DoT) and lock settings.
         */
        fun enforcePrivateDns(context: Context, dnsHostname: String = "family.cloudflare-dns.com") {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            try {
                // Set system-wide private DNS mode to strict hostname
                // (Android Settings.Global constants for Private DNS)
                dpm.setGlobalSetting(admin, "private_dns_mode", "hostname")
                dpm.setGlobalSetting(admin, "private_dns_specifier", dnsHostname)
            } catch (ignored: Exception) {}

            // Lock the Private DNS settings so user cannot toggle or change it
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }

        /**
         * Tier 2 Mode B: Enforce RethinkDNS as Always-On VPN with OS Killswitch.
         * If RethinkDNS is turned off or killed, Android immediately cuts 100% of network traffic.
         */
        fun enforceRethinkCompanion(context: Context, rethinkPackage: String = "com.celzero.bravedns") {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            // 1. Protect RethinkDNS from being uninstalled
            dpm.setUninstallBlocked(admin, rethinkPackage, true)

            // 2. Lock down the VPN with system killswitch (lockdownEnabled = true)
            dpm.setAlwaysOnVpnPackage(admin, rethinkPackage, true)

            // 3. Prevent tampering with VPN settings in Android Settings
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
        }

        /**
         * Relaxes restrictions temporarily when the 24h quarantine and gauntlet are passed.
         */
        fun relaxRestrictions(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)
            dpm.setUninstallBlocked(admin, context.packageName, false)
        }
    }
}
