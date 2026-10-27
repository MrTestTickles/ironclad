package io.github.ironclad.dns

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-throughput asynchronous DNS proxy engine.
 *
 * Per-packet pipeline:
 *  1. Parse IP header (IPv4 or IPv6) → get protocol, dstIp, dstPort
 *  2. BypassBlocker.classify() → silently drop DoT (port 853), DoH (port 443 to known IPs),
 *     and rogue plain DNS queries
 *  3. For UDP port 53:
 *     a. Sinkhole adult domains
 *     b. Enforce SafeSearch VIPs (Google, Bing, DuckDuckGo, YouTube)
 *     c. Forward legitimate queries to upstream family-safe resolvers
 *  4. All other packets: silently dropped
 */
class DnsProxyEngine(
    private val vpnService: VpnService,
    private val vpnInterface: ParcelFileDescriptor
) {

    private val isRunning = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private val workerPool = Executors.newFixedThreadPool(8)
    private val outStreamLock = Any()

    companion object {
        private const val TAG = "IroncladDns"
        const val UPSTREAM_PRIMARY   = "1.1.1.3"         // Cloudflare Family (adult + malware blocking)
        const val UPSTREAM_SECONDARY = "185.228.168.168" // CleanBrowsing Family Filter
        private const val SOCKET_TIMEOUT_MS = 2500
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        readerThread = Thread({ runTunLoop() }, "IroncladTunReader").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        readerThread?.interrupt()
        workerPool.shutdownNow()
        readerThread = null
    }

    private fun runTunLoop() {
        val inStream  = FileInputStream(vpnInterface.fileDescriptor)
        val outStream = FileOutputStream(vpnInterface.fileDescriptor)
        val buffer    = ByteArray(4096)

        while (isRunning.get()) {
            try {
                val length = inStream.read(buffer)
                if (length <= 0) continue

                val packet = buffer.copyOf(length)

                // --- Step 1: Parse IP metadata (protocol, IPs, ports) ---
                val meta = IpPacketHandler.parseMeta(packet, length) ?: continue

                // --- Step 2: Bypass detection (DoT / DoH / rogue DNS) ---
                val bypass = BypassBlocker.classify(meta.protocol, meta.dstPort, meta.dstIpStr)
                if (bypass != null) {
                    Log.w(TAG, "🛡 BYPASS BLOCKED [${bypass.name}] → ${meta.dstIpStr}:${meta.dstPort}")
                    continue  // silently drop
                }

                // --- Step 3: DNS processing (sinkhole + SafeSearch + upstream) ---
                if (meta.protocol == IpPacketHandler.PROTOCOL_UDP &&
                    meta.dstPort == IpPacketHandler.DNS_PORT) {

                    val request = IpPacketHandler.parseUdpDns(packet, length) ?: continue
                    val dnsQuery = DnsPacketParser.parseQuery(
                        request.dnsPayload, 0, request.dnsPayload.size
                    )

                    if (dnsQuery != null) {
                        val isIpv6Query = (dnsQuery.qType == DnsPacketParser.QTYPE_AAAA)

                        // 3a. Adult Content Sinkhole
                        if (AdultContentFilter.isBlocked(dnsQuery.domain)) {
                            Log.i(TAG, "🚫 SINKHOLE: ${dnsQuery.domain}")
                            val blockedDns = DnsPacketParser.buildBlockedResponse(dnsQuery, isIpv6 = isIpv6Query)
                            sendReply(request, blockedDns, outStream)
                            continue
                        }

                        // 3b. SafeSearch Enforcement
                        val safeSearchIp = SafeSearchEnforcer.getSafeSearchIp(dnsQuery.domain, isIpv6 = isIpv6Query)
                        if (safeSearchIp != null) {
                            Log.i(TAG, "🔒 SAFESEARCH ENFORCED: ${dnsQuery.domain}")
                            val safeSearchDns = DnsPacketParser.buildIpResponse(dnsQuery, safeSearchIp)
                            sendReply(request, safeSearchDns, outStream)
                            continue
                        }
                    }

                    // 3c. Upstream Family Resolution
                    workerPool.execute { handleUpstreamResolution(request, outStream) }
                }

            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun sendReply(request: UdpDnsRequest, dnsPayload: ByteArray, outStream: FileOutputStream) {
        val reply = if (request.isIpv6) {
            IpPacketHandler.buildIpv6UdpPacket(
                srcIp      = request.dstIp,
                dstIp      = request.srcIp,
                srcPort    = request.dstPort,
                dstPort    = request.srcPort,
                udpPayload = dnsPayload
            )
        } else {
            IpPacketHandler.buildIpUdpPacket(
                srcIp      = request.dstIp,
                dstIp      = request.srcIp,
                srcPort    = request.dstPort,
                dstPort    = request.srcPort,
                udpPayload = dnsPayload
            )
        }
        synchronized(outStreamLock) {
            outStream.write(reply)
        }
    }

    private fun handleUpstreamResolution(request: UdpDnsRequest, outStream: FileOutputStream) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            if (!vpnService.protect(socket)) {
                Log.w(TAG, "Failed to protect upstream socket")
                return
            }
            socket.soTimeout = SOCKET_TIMEOUT_MS

            val queryPacket = DatagramPacket(
                request.dnsPayload,
                request.dnsPayload.size,
                InetAddress.getByName(UPSTREAM_PRIMARY),
                IpPacketHandler.DNS_PORT
            )
            val recvBuffer  = ByteArray(2048)
            val responsePacket = DatagramPacket(recvBuffer, recvBuffer.size)

            try {
                socket.send(queryPacket)
                socket.receive(responsePacket)
            } catch (timeout: Exception) {
                // Failover to CleanBrowsing
                queryPacket.address = InetAddress.getByName(UPSTREAM_SECONDARY)
                socket.send(queryPacket)
                socket.receive(responsePacket)
            }

            val dnsResponse = responsePacket.data.copyOf(responsePacket.length)
            sendReply(request, dnsResponse, outStream)
        } catch (ignored: Exception) {
            // Timeout → client's resolver retries naturally
        } finally {
            socket?.close()
        }
    }
}
