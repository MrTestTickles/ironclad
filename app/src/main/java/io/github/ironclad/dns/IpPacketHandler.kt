package io.github.ironclad.dns

import java.net.InetAddress
import java.nio.ByteBuffer

data class UdpDnsRequest(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dnsPayload: ByteArray,
    val isIpv6: Boolean = false
)

/** Lightweight metadata parsed from any IP packet (IPv4 or IPv6, TCP or UDP). */
data class IpPacketMeta(
    val isIpv6: Boolean,
    val protocol: Int,   // 6=TCP, 17=UDP
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val dstIpStr: String,
    val srcPort: Int,
    val dstPort: Int
)

object IpPacketHandler {

    private const val IP_VERSION_4 = 4
    private const val IP_VERSION_6 = 6
    const val PROTOCOL_UDP = 17
    const val PROTOCOL_TCP = 6
    const val DNS_PORT = 53

    /**
     * Parses IP metadata (version, protocol, IPs, ports) from an IPv4 or IPv6 packet.
     * Returns null for non-IP or malformed packets.
     */
    fun parseMeta(packet: ByteArray, length: Int): IpPacketMeta? {
        if (length < 28) return null
        val version = (packet[0].toInt() and 0xF0) ushr 4

        return when (version) {
            IP_VERSION_4 -> parseIpv4Meta(packet, length)
            IP_VERSION_6 -> parseIpv6Meta(packet, length)
            else -> null
        }
    }

    private fun parseIpv4Meta(packet: ByteArray, length: Int): IpPacketMeta? {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ihl + 4 > length) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP && protocol != PROTOCOL_TCP) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val dstIpStr = "${dstIp[0].toInt() and 0xFF}.${dstIp[1].toInt() and 0xFF}" +
                ".${dstIp[2].toInt() and 0xFF}.${dstIp[3].toInt() and 0xFF}"

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        return IpPacketMeta(isIpv6 = false, protocol, srcIp, dstIp, dstIpStr, srcPort, dstPort)
    }

    private fun parseIpv6Meta(packet: ByteArray, length: Int): IpPacketMeta? {
        if (length < 40 + 4) return null // 40-byte IPv6 header + at least 4 bytes of ports

        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != PROTOCOL_UDP && nextHeader != PROTOCOL_TCP) return null

        val srcIp = packet.copyOfRange(8, 24)
        val dstIp = packet.copyOfRange(24, 40)
        val dstIpStr = try {
            InetAddress.getByAddress(dstIp).hostAddress ?: ""
        } catch (_: Exception) {
            ""
        }

        val srcPort = ((packet[40].toInt() and 0xFF) shl 8) or (packet[41].toInt() and 0xFF)
        val dstPort = ((packet[42].toInt() and 0xFF) shl 8) or (packet[43].toInt() and 0xFF)

        return IpPacketMeta(isIpv6 = true, nextHeader, srcIp, dstIp, dstIpStr, srcPort, dstPort)
    }

    /**
     * Inspects raw IP packet from TUN interface and extracts UDP DNS queries (port 53).
     * Works for both IPv4 and IPv6.
     */
    fun parseUdpDns(packet: ByteArray, length: Int): UdpDnsRequest? {
        val meta = parseMeta(packet, length) ?: return null
        if (meta.protocol != PROTOCOL_UDP || meta.dstPort != DNS_PORT) return null

        val (headerLen, udpOffset) = if (!meta.isIpv6) {
            val ihl = (packet[0].toInt() and 0x0F) * 4
            ihl to ihl
        } else {
            40 to 40
        }

        if (udpOffset + 8 > length) return null

        val udpLen = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)
        val dnsLen = udpLen - 8
        if (dnsLen <= 0 || udpOffset + 8 + dnsLen > length) return null

        val dnsPayload = ByteArray(dnsLen)
        System.arraycopy(packet, udpOffset + 8, dnsPayload, 0, dnsLen)

        return UdpDnsRequest(
            srcIp = meta.srcIp,
            dstIp = meta.dstIp,
            srcPort = meta.srcPort,
            dstPort = meta.dstPort,
            dnsPayload = dnsPayload,
            isIpv6 = meta.isIpv6
        )
    }

    /**
     * Constructs a full IPv4 + UDP packet wrapping a DNS response payload.
     */
    fun buildIpUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        udpPayload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + udpPayload.size
        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // --- IPv4 Header (20 bytes) ---
        buffer.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        buffer.put(0x00.toByte()) // DSCP/ECN
        buffer.putShort(totalLen.toShort()) // Total length
        buffer.putShort(0x0000.toShort()) // Identification
        buffer.putShort(0x4000.toShort()) // Flags (Don't Fragment) + Fragment offset (0)
        buffer.put(64.toByte()) // TTL (64)
        buffer.put(PROTOCOL_UDP.toByte()) // Protocol (17 = UDP)
        buffer.putShort(0.toShort()) // Checksum placeholder
        buffer.put(srcIp) // Source IP
        buffer.put(dstIp) // Dest IP

        // Compute and inject IPv4 Header Checksum
        val ipChecksum = computeIpChecksum(packet, 0, ipHeaderLen)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // --- UDP Header (8 bytes) ---
        buffer.position(ipHeaderLen)
        buffer.putShort(srcPort.toShort()) // Source Port
        buffer.putShort(dstPort.toShort()) // Dest Port
        buffer.putShort((udpHeaderLen + udpPayload.size).toShort()) // UDP Length
        buffer.putShort(0x0000.toShort()) // UDP Checksum (0x0000 = disabled in IPv4)

        // --- UDP Payload (DNS Response) ---
        buffer.put(udpPayload)

        return packet
    }

    /**
     * Constructs a full IPv6 + UDP packet wrapping a DNS response payload.
     */
    fun buildIpv6UdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        udpPayload: ByteArray
    ): ByteArray {
        val ipHeaderLen = 40
        val udpHeaderLen = 8
        val udpTotalLen = udpHeaderLen + udpPayload.size
        val totalLen = ipHeaderLen + udpTotalLen
        val packet = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(packet)

        // --- IPv6 Header (40 bytes) ---
        buffer.put(0x60.toByte()) // Version 6, Traffic Class top 4 bits 0
        buffer.put(0x00.toByte()) // Traffic Class lower 4 bits, Flow Label top 4 bits
        buffer.putShort(0x0000.toShort()) // Flow Label remaining 16 bits
        buffer.putShort(udpTotalLen.toShort()) // Payload length (transport + data)
        buffer.put(PROTOCOL_UDP.toByte()) // Next Header = UDP (17)
        buffer.put(64.toByte()) // Hop Limit
        buffer.put(srcIp) // Source IPv6 (16 bytes)
        buffer.put(dstIp) // Dest IPv6 (16 bytes)

        // --- UDP Header (8 bytes) ---
        buffer.position(ipHeaderLen)
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort(udpTotalLen.toShort())
        buffer.putShort(0x0000.toShort()) // Checksum placeholder

        // --- UDP Payload ---
        buffer.put(udpPayload)

        // Compute IPv6 UDP pseudo-header checksum
        val udpChecksum = computeIpv6UdpChecksum(srcIp, dstIp, udpTotalLen, packet, ipHeaderLen)
        packet[ipHeaderLen + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
        packet[ipHeaderLen + 7] = (udpChecksum and 0xFF).toByte()

        return packet
    }

    /**
     * Computes Internet Checksum (RFC 1071) over the given byte range.
     */
    fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            sum += ((high shl 8) or low)
            i += 2
        }
        if (i < offset + length) {
            sum += ((data[i].toInt() and 0xFF) shl 8)
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun computeIpv6UdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        udpLen: Int,
        packet: ByteArray,
        udpOffset: Int
    ): Int {
        var sum = 0L
        // Pseudo-header: Src (16) + Dst (16) + UdpLen (4) + NextHeader (4)
        for (i in 0 until 16 step 2) {
            sum += (((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF))
            sum += (((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF))
        }
        sum += udpLen
        sum += PROTOCOL_UDP

        // UDP packet bytes
        var i = udpOffset
        val end = udpOffset + udpLen
        while (i < end - 1) {
            sum += (((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF))
            i += 2
        }
        if (i < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8)
        }

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val result = (sum.inv() and 0xFFFF).toInt()
        return if (result == 0) 0xFFFF else result
    }
}
