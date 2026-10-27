package io.github.ironclad.dns

import org.junit.Assert.*
import org.junit.Test

class BypassBlockerTest {

    @Test
    fun testDoTBlockingOnPort853() {
        // TCP to port 853 should be classified as DNS_OVER_TLS
        val tcpDot = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_TCP,
            dstPort = 853,
            dstIpStr = "1.1.1.1"
        )
        assertEquals(BypassBlocker.BypassType.DNS_OVER_TLS, tcpDot)

        // UDP to port 853 (e.g. DoQ or DoT) should also be classified
        val udpDot = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_UDP,
            dstPort = 853,
            dstIpStr = "8.8.8.8"
        )
        assertEquals(BypassBlocker.BypassType.DNS_OVER_TLS, udpDot)
    }

    @Test
    fun testDoHBlockingOnPort443ToKnownResolvers() {
        // Cloudflare DoH IP
        val cfDoh = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_TCP,
            dstPort = 443,
            dstIpStr = "1.1.1.1"
        )
        assertEquals(BypassBlocker.BypassType.DNS_OVER_HTTPS, cfDoh)

        // Google DoH IP
        val googleDoh = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_TCP,
            dstPort = 443,
            dstIpStr = "8.8.8.8"
        )
        assertEquals(BypassBlocker.BypassType.DNS_OVER_HTTPS, googleDoh)

        // Quad9 DoH IP
        val quad9Doh = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_TCP,
            dstPort = 443,
            dstIpStr = "9.9.9.9"
        )
        assertEquals(BypassBlocker.BypassType.DNS_OVER_HTTPS, quad9Doh)

        // Mullvad DoH IP
        val mullvadDoh = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_TCP,
            dstPort = 443,
            dstIpStr = "194.242.2.2"
        )
        assertEquals(BypassBlocker.BypassType.DNS_OVER_HTTPS, mullvadDoh)

        // Regular HTTPS to a non-DoH web server must NOT be blocked
        val regularWeb = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_TCP,
            dstPort = 443,
            dstIpStr = "142.250.190.46" // google.com web server
        )
        assertNull(regularWeb)
    }

    @Test
    fun testRogueDnsBlocking() {
        val rogueDns = BypassBlocker.classify(
            protocol = BypassBlocker.PROTO_UDP,
            dstPort = 53,
            dstIpStr = "8.8.8.8"
        )
        assertEquals(BypassBlocker.BypassType.ROGUE_DNS, rogueDns)
    }

    @Test
    fun testSafeSearchEnforcer_googleDomains() {
        val googleIpv4 = SafeSearchEnforcer.getSafeSearchIp("google.com", isIpv6 = false)
        assertNotNull(googleIpv4)
        assertArrayEquals(SafeSearchEnforcer.GOOGLE_IPV4, googleIpv4)

        val googleWww = SafeSearchEnforcer.getSafeSearchIp("www.google.co.uk", isIpv6 = false)
        assertNotNull(googleWww)
        assertArrayEquals(SafeSearchEnforcer.GOOGLE_IPV4, googleWww)

        val googleIpv6 = SafeSearchEnforcer.getSafeSearchIp("www.google.com", isIpv6 = true)
        assertNotNull(googleIpv6)
        assertArrayEquals(SafeSearchEnforcer.GOOGLE_IPV6, googleIpv6)
    }

    @Test
    fun testSafeSearchEnforcer_bingAndYouTubeAndDuckDuckGo() {
        // Bing
        val bingIpv4 = SafeSearchEnforcer.getSafeSearchIp("bing.com", isIpv6 = false)
        assertArrayEquals(SafeSearchEnforcer.BING_IPV4, bingIpv4)

        // YouTube
        val ytIpv4 = SafeSearchEnforcer.getSafeSearchIp("m.youtube.com", isIpv6 = false)
        assertArrayEquals(SafeSearchEnforcer.YOUTUBE_IPV4, ytIpv4)

        // DuckDuckGo
        val ddgIpv4 = SafeSearchEnforcer.getSafeSearchIp("duckduckgo.com", isIpv6 = false)
        assertArrayEquals(SafeSearchEnforcer.DUCKDUCKGO_IPV4, ddgIpv4)

        // Non-search domains
        assertNull(SafeSearchEnforcer.getSafeSearchIp("wikipedia.org", isIpv6 = false))
        assertNull(SafeSearchEnforcer.getSafeSearchIp("github.com", isIpv6 = false))
    }

    @Test
    fun testDoHSubdomainWalkInAdultFilter() {
        // Cloudflare DoH subdomain
        assertTrue(AdultContentFilter.isBlocked("1dot1dot1dot1.cloudflare-dns.com"))
        assertTrue(AdultContentFilter.isBlocked("security.cloudflare-dns.com"))

        // NextDNS custom subdomains
        assertTrue(AdultContentFilter.isBlocked("my-profile.dns.nextdns.io"))

        // AdGuard subdomains
        assertTrue(AdultContentFilter.isBlocked("unfiltered.dns.adguard.com"))
    }
}
