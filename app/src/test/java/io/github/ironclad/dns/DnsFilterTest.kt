package io.github.ironclad.dns

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DnsFilterTest {

    @Test
    fun testAdultContentFilter_blockedDomains() {
        // Direct domain match
        assertTrue(AdultContentFilter.isBlocked("pornhub.com"))
        assertTrue(AdultContentFilter.isBlocked("xvideos.com"))
        assertTrue(AdultContentFilter.isBlocked("chaturbate.com"))
        assertTrue(AdultContentFilter.isBlocked("nhentai.net"))

        // Subdomains
        assertTrue(AdultContentFilter.isBlocked("m.pornhub.com"))
        assertTrue(AdultContentFilter.isBlocked("video.xvideos.com"))
        assertTrue(AdultContentFilter.isBlocked("static.chaturbate.com"))

        // Normalization (case insensitivity, trailing dot)
        assertTrue(AdultContentFilter.isBlocked("PORNHUB.COM."))
        assertTrue(AdultContentFilter.isBlocked("  xVideos.Com  "))
    }

    @Test
    fun testAdultContentFilter_blockedTlds() {
        assertTrue(AdultContentFilter.isBlocked("something.xxx"))
        assertTrue(AdultContentFilter.isBlocked("gallery.adult"))
        assertTrue(AdultContentFilter.isBlocked("myblog.porn"))
        assertTrue(AdultContentFilter.isBlocked("content.sex"))
    }

    @Test
    fun testAdultContentFilter_blockedKeywords() {
        assertTrue(AdultContentFilter.isBlocked("free-porn-videos.net"))
        assertTrue(AdultContentFilter.isBlocked("hentai-stream.org"))
        assertTrue(AdultContentFilter.isBlocked("erotic-photos.io"))
    }

    @Test
    fun testAdultContentFilter_blockedDohBypasses() {
        assertTrue(AdultContentFilter.isBlocked("cloudflare-dns.com"))
        assertTrue(AdultContentFilter.isBlocked("dns.google"))
        assertTrue(AdultContentFilter.isBlocked("dns.quad9.net"))
    }

    @Test
    fun testAdultContentFilter_allowedLegitimateDomains() {
        assertFalse(AdultContentFilter.isBlocked("google.com"))
        assertFalse(AdultContentFilter.isBlocked("wikipedia.org"))
        assertFalse(AdultContentFilter.isBlocked("github.com"))
        assertFalse(AdultContentFilter.isBlocked("stackoverflow.com"))
        assertFalse(AdultContentFilter.isBlocked("apple.com"))
    }

    @Test
    fun testDnsPacketParser_parseAndBuildResponse() {
        // Construct simulated DNS Query for "pornhub.com" (Type A)
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0x4321) // ID
        dos.writeShort(0x0100) // Flags: Standard query
        dos.writeShort(1)      // QDCOUNT
        dos.writeShort(0)      // ANCOUNT
        dos.writeShort(0)      // NSCOUNT
        dos.writeShort(0)      // ARCOUNT

        // QNAME: \x07pornhub\x03com\x00
        dos.writeByte(7)
        dos.write("pornhub".toByteArray(Charsets.US_ASCII))
        dos.writeByte(3)
        dos.write("com".toByteArray(Charsets.US_ASCII))
        dos.writeByte(0)

        // QTYPE=1 (A), QCLASS=1 (IN)
        dos.writeShort(1)
        dos.writeShort(1)
        dos.flush()

        val queryBytes = baos.toByteArray()
        val parsed = DnsPacketParser.parseQuery(queryBytes, 0, queryBytes.size)

        assertNotNull(parsed)
        assertEquals(0x4321, parsed!!.id)
        assertEquals("pornhub.com", parsed.domain)
        assertEquals(1, parsed.qType)
        assertEquals(1, parsed.qClass)

        // Build blocked response (sinkhole)
        val responseBytes = DnsPacketParser.buildBlockedResponse(parsed, isIpv6 = false)
        assertTrue(responseBytes.size > queryBytes.size)

        // Verify response header
        val respId = ((responseBytes[0].toInt() and 0xFF) shl 8) or (responseBytes[1].toInt() and 0xFF)
        val respFlags = ((responseBytes[2].toInt() and 0xFF) shl 8) or (responseBytes[3].toInt() and 0xFF)
        val anCount = ((responseBytes[6].toInt() and 0xFF) shl 8) or (responseBytes[7].toInt() and 0xFF)

        assertEquals(0x4321, respId)
        assertEquals(0x8180, respFlags)
        assertEquals(1, anCount)

        // Verify the 4-byte RDATA at the end is 0.0.0.0
        val rdata = responseBytes.copyOfRange(responseBytes.size - 4, responseBytes.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), rdata)
    }

    @Test
    fun testIpPacketHandler_parseAndBuildUdp() {
        val srcIp = byteArrayOf(10, 10, 0, 1)
        val dstIp = byteArrayOf(10, 10, 0, 2)
        val dnsPayload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        val packet = IpPacketHandler.buildIpUdpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 12345,
            dstPort = 53,
            udpPayload = dnsPayload
        )

        assertEquals(20 + 8 + dnsPayload.size, packet.size)

        // Verify IPv4 header checksum
        val checksum = IpPacketHandler.computeIpChecksum(packet, 0, 20)
        assertEquals(0, checksum)

        // Parse it back
        val parsed = IpPacketHandler.parseUdpDns(packet, packet.size)
        assertNotNull(parsed)
        assertArrayEquals(srcIp, parsed!!.srcIp)
        assertArrayEquals(dstIp, parsed.dstIp)
        assertEquals(12345, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(dnsPayload, parsed.dnsPayload)
    }

    @Test
    fun testIpPacketHandler_ipv6UdpPacketBuildAndParse() {
        val srcIp = ByteArray(16).apply { this[15] = 1 } // ::1
        val dstIp = ByteArray(16).apply { this[15] = 2 } // ::2
        val dnsPayload = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00)

        val packet = IpPacketHandler.buildIpv6UdpPacket(
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = 54321,
            dstPort = 53,
            udpPayload = dnsPayload
        )

        // 40 bytes IPv6 header + 8 bytes UDP header + payload
        assertEquals(40 + 8 + dnsPayload.size, packet.size)

        // Parse meta
        val meta = IpPacketHandler.parseMeta(packet, packet.size)
        assertNotNull(meta)
        assertTrue(meta!!.isIpv6)
        assertEquals(IpPacketHandler.PROTOCOL_UDP, meta.protocol)
        assertEquals(54321, meta.srcPort)
        assertEquals(53, meta.dstPort)

        // Parse UDP DNS
        val parsed = IpPacketHandler.parseUdpDns(packet, packet.size)
        assertNotNull(parsed)
        assertTrue(parsed!!.isIpv6)
        assertArrayEquals(srcIp, parsed.srcIp)
        assertArrayEquals(dstIp, parsed.dstIp)
        assertEquals(54321, parsed.srcPort)
        assertEquals(53, parsed.dstPort)
        assertArrayEquals(dnsPayload, parsed.dnsPayload)
    }

    @Test
    fun testDnsPacketParser_buildIpResponse() {
        val queryBytes = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x03, 'w'.code.toByte(),
            'w'.code.toByte(), 'w'.code.toByte(), 0x00, 0x00, 0x01, 0x00, 0x01
        )
        val parsed = DnsPacketParser.parseQuery(queryBytes, 0, queryBytes.size)
        assertNotNull(parsed)

        val targetIp = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte())
        val response = DnsPacketParser.buildIpResponse(parsed!!, targetIp)

        // Verify response ends with the target IP
        val rdata = response.copyOfRange(response.size - 4, response.size)
        assertArrayEquals(targetIp, rdata)
    }
}
