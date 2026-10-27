package io.github.ironclad.dns

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

object DnsPacketParser {

    const val QTYPE_A = 1
    const val QTYPE_AAAA = 28

    /**
     * Parses a DNS query packet from raw byte data.
     */
    fun parseQuery(data: ByteArray, offset: Int, length: Int): DnsPacket? {
        if (length < 12) return null

        val buffer = ByteBuffer.wrap(data, offset, length)
        val id = buffer.short.toInt() and 0xFFFF
        val flags = buffer.short.toInt() and 0xFFFF

        // Only handle queries (QR bit = 0)
        val isQuery = (flags and 0x8000) == 0
        if (!isQuery) return null

        val qdCount = buffer.short.toInt() and 0xFFFF
        if (qdCount < 1) return null

        // Skip ANCOUNT, NSCOUNT, ARCOUNT
        buffer.short
        buffer.short
        buffer.short

        val qStart = buffer.position()
        val domainParts = mutableListOf<String>()

        while (buffer.hasRemaining()) {
            val len = buffer.get().toInt() and 0xFF
            if (len == 0) break
            if (len > buffer.remaining()) return null // Malformed

            val labelBytes = ByteArray(len)
            buffer.get(labelBytes)
            domainParts.add(String(labelBytes, Charsets.US_ASCII))
        }

        if (buffer.remaining() < 4) return null
        val qType = buffer.short.toInt() and 0xFFFF
        val qClass = buffer.short.toInt() and 0xFFFF
        val qEnd = buffer.position()

        val qBytes = ByteArray(qEnd - qStart)
        System.arraycopy(data, offset + qStart, qBytes, 0, qBytes.size)

        val domain = domainParts.joinToString(".")
        return DnsPacket(
            id = id,
            flags = flags,
            qdCount = qdCount,
            domain = domain,
            qType = qType,
            qClass = qClass,
            questionBytes = qBytes
        )
    }

    /**
     * Builds a sinkhole response (0.0.0.0 for IPv4 A, or :: for IPv6 AAAA).
     */
    fun buildBlockedResponse(query: DnsPacket, isIpv6: Boolean = query.qType == QTYPE_AAAA): ByteArray {
        val targetIp = if (isIpv6) ByteArray(16) else ByteArray(4)
        return buildIpResponse(query, targetIp)
    }

    /**
     * Builds a synthetic DNS response pointing to a specific IP address (e.g. SafeSearch VIP).
     */
    fun buildIpResponse(query: DnsPacket, ipBytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. Transaction ID
        dos.writeShort(query.id)

        // 2. Flags: Response (0x8000) + Authoritative (0x0400) + Recursion Desired (0x0100) + Recursion Available (0x0080)
        // 0x8180 = Standard query response, No error
        dos.writeShort(0x8180)

        // 3. Counts: 1 Question, 1 Answer, 0 Authority, 0 Additional
        dos.writeShort(1) // QDCOUNT
        dos.writeShort(1) // ANCOUNT
        dos.writeShort(0) // NSCOUNT
        dos.writeShort(0) // ARCOUNT

        // 4. Question Section (copied verbatim)
        dos.write(query.questionBytes)

        // 5. Answer Section
        // Name: Pointer to QNAME at byte 12 (0xC00C)
        dos.writeShort(0xC00C)

        if (ipBytes.size == 16) {
            dos.writeShort(QTYPE_AAAA)
            dos.writeShort(1) // Class IN
            dos.writeInt(300) // TTL 300 seconds
            dos.writeShort(16) // RDLENGTH 16 bytes
            dos.write(ipBytes)
        } else {
            dos.writeShort(QTYPE_A)
            dos.writeShort(1) // Class IN
            dos.writeInt(300) // TTL 300 seconds
            dos.writeShort(4) // RDLENGTH 4 bytes
            dos.write(ipBytes)
        }

        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Builds a NODATA response (NOERROR with ANCOUNT = 0) so the client falls back
     * to alternative record types (e.g. A instead of AAAA).
     */
    fun buildEmptyResponse(query: DnsPacket): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeShort(query.id)
        dos.writeShort(0x8180) // NOERROR
        dos.writeShort(1)      // QDCOUNT = 1
        dos.writeShort(0)      // ANCOUNT = 0
        dos.writeShort(0)      // NSCOUNT = 0
        dos.writeShort(0)      // ARCOUNT = 0
        dos.write(query.questionBytes)
        dos.flush()
        return baos.toByteArray()
    }
}
