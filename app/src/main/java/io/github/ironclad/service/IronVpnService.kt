package io.github.ironclad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.app.admin.DevicePolicyManager
import android.util.Log
import io.github.ironclad.dns.AdultContentFilter
import io.github.ironclad.dns.BypassBlocker
import io.github.ironclad.dns.DnsProxyEngine
import io.github.ironclad.ui.MainActivity

/**
 * Tier 1 Local VPN: Routes DNS lookups into a high-performance local packet engine
 * that instantly sinkholes adult websites, enforces SafeSearch, and forwards standard
 * queries to privacy-respecting, family-filtered resolvers
 * (Cloudflare Family 1.1.1.3 + CleanBrowsing 185.228.168.168)
 * without tunneling heavy non-DNS traffic.
 */
class IronVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyEngine: DnsProxyEngine? = null

    companion object {
        const val ACTION_START = "io.github.ironclad.START_VPN"
        const val ACTION_STOP = "io.github.ironclad.STOP_VPN"
        private const val CHANNEL_ID = "ironclad_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "IronVpnService"
        const val PREFS_NAME = "ironclad_vpn_prefs"
        const val KEY_VPN_ACTIVE = "vpn_active"

        const val VIRTUAL_DNS_IP = "10.10.0.2"
        const val VIRTUAL_DNS_IPV6 = "fd00:10:10::2"

        var isRunning = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        try {
            val builder = Builder()
                .setSession("Ironclad Shield")
                .setMtu(1500)
                // IPv4 Virtual Adapter & DNS
                .addAddress("10.10.0.1", 30)
                .addDnsServer(VIRTUAL_DNS_IP)
                .addRoute(VIRTUAL_DNS_IP, 32)
                // IPv6 Virtual Adapter & DNS (prevents IPv6 DNS leak/bypass)
                .apply {
                    try {
                        addAddress("fd00:10:10::1", 126)
                        addDnsServer(VIRTUAL_DNS_IPV6)
                        addRoute(VIRTUAL_DNS_IPV6, 128)
                    } catch (e: Exception) {
                        Log.w(TAG, "IPv6 route setup warning: ${e.message}")
                    }
                }
                // Route ALL known DoH/DoT resolver IPs into tunnel so BypassBlocker can drop them
                .apply {
                    BypassBlocker.DOH_SERVER_IPS.forEach { ip ->
                        try {
                            if (ip.contains(':')) {
                                addRoute(ip, 128)
                            } else {
                                addRoute(ip, 32)
                            }
                        } catch (_: Exception) {}
                    }
                }
                .setBlocking(true)

            // Disallow Ironclad from entering the VPN tunnel so our own sockets route natively
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not add disallowed application: ${e.message}")
            }

            val pfd = builder.establish()
            if (pfd == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopVpn()
                return
            }

            vpnInterface = pfd
            // Pre-load the 1.2M domain blocklist asset on a background thread
            Thread { AdultContentFilter.initialize(this) }.start()

            proxyEngine = DnsProxyEngine(this, pfd).apply {
                start()
            }

            isRunning = true
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VPN_ACTIVE, true)
                .apply()

            enforceAlwaysOnLockdown()
            Log.i(TAG, "Ironclad DNS Filtering Engine successfully started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Ironclad VPN", e)
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VPN_ACTIVE, false)
            .apply()

        try {
            proxyEngine?.stop()
        } catch (ignored: Exception) {}
        proxyEngine = null

        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * If we are Device Owner, lock ourselves in as always-on VPN with killswitch.
     * This means: if Ironclad VPN is killed or crashes, Android cuts ALL network traffic
     * until it restarts — preventing any bypass window.
     */
    private fun enforceAlwaysOnLockdown() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = io.github.ironclad.admin.IronDeviceAdminReceiver.getComponentName(this)
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setAlwaysOnVpnPackage(admin, packageName, /* lockdownEnabled= */ true)
            }
        } catch (ignored: Exception) {}
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ironclad Shield Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Protects device from adult content."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Ironclad Protection Active")
            .setContentText("DNS content filter is actively safeguarding this device.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
