package io.github.ironclad.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import io.github.ironclad.ui.MainActivity

/**
 * Tier 1 Local VPN: Routes DNS through privacy-respecting, family-filtered resolvers
 * (Cloudflare Family 1.1.1.3 + CleanBrowsing 185.228.168.168) without routing all heavy traffic,
 * maintaining native network performance.
 */
class IronVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START = "io.github.ironclad.START_VPN"
        const val ACTION_STOP = "io.github.ironclad.STOP_VPN"
        private const val CHANNEL_ID = "ironclad_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        // Adult & Malware Blocking DNS Resolvers
        const val DNS_CLOUDFLARE_FAMILY = "1.1.1.3"
        const val DNS_CLEANBROWSING_FAMILY = "185.228.168.168"

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
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            vpnInterface = Builder()
                .setSession("Ironclad Shield")
                .addAddress("10.10.0.1", 32)
                .addDnsServer(DNS_CLOUDFLARE_FAMILY)
                .addDnsServer(DNS_CLEANBROWSING_FAMILY)
                // Route only DNS IPs into the TUN adapter to keep regular traffic full speed
                .addRoute(DNS_CLOUDFLARE_FAMILY, 32)
                .addRoute(DNS_CLEANBROWSING_FAMILY, 32)
                .setBlocking(true)
                .establish()

            isRunning = true
        } catch (e: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (ignored: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
