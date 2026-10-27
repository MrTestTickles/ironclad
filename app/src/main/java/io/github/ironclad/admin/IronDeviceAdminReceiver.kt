package io.github.ironclad.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

class IronDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Ironclad Device Admin Enabled", Toast.LENGTH_SHORT).show()
        enforceDefaultPolicies(context)
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
         * Enforces foundational enterprise anti-tamper & anti-bypass restrictions.
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

            // 4. Prevent factory resets
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)

            // 5. Prevent tampering with VPN configurations
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_VPN)

            // 6. Prevent tampering with Private DNS settings
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)

            // 7. Prevent modifying system Date/Time to bypass quarantine countdown
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)

            // 8. Prevent USB debugging / ADB shell bypasses while locked
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)

            // 9. Lock Ironclad as Always-on VPN with system-level network lockdown killswitch
            try {
                dpm.setAlwaysOnVpnPackage(admin, context.packageName, /* lockdownEnabled= */ true)
            } catch (ignored: Exception) {}
        }

        /**
         * Tier 2 Mode A: Enforce system Private DNS (DoT) and lock settings.
         */
        fun enforcePrivateDns(context: Context, dnsHostname: String = "family.cloudflare-dns.com") {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            try {
                dpm.setGlobalSetting(admin, "private_dns_mode", "hostname")
                dpm.setGlobalSetting(admin, "private_dns_specifier", dnsHostname)
            } catch (ignored: Exception) {}

            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        }

        /**
         * Tier 2 Mode B: Enforce RethinkDNS as Always-On VPN with OS Killswitch.
         */
        fun enforceRethinkCompanion(context: Context, rethinkPackage: String = "com.celzero.bravedns") {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)
            if (!dpm.isDeviceOwnerApp(context.packageName)) return

            dpm.setUninstallBlocked(admin, rethinkPackage, true)
            dpm.setAlwaysOnVpnPackage(admin, rethinkPackage, true)
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
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.setUninstallBlocked(admin, context.packageName, false)

            try {
                dpm.setAlwaysOnVpnPackage(admin, null, false)
            } catch (ignored: Exception) {}
        }
    }
}
