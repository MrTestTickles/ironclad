package io.github.ironclad.dns

import java.util.Locale

/**
 * Enforces strict SafeSearch and YouTube Restricted Mode at the DNS level.
 *
 * Intercepts DNS queries for major search engines and responds with their official
 * VIP addresses, forcing SafeSearch even in Incognito / Private Browsing mode
 * with no way to turn it off in browser settings or cookies.
 */
object SafeSearchEnforcer {

    // Official SafeSearch VIPs
    // Google: forcesafesearch.google.com
    val GOOGLE_IPV4: ByteArray = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte()) // 216.239.38.120
    val GOOGLE_IPV6: ByteArray = byteArrayOf(
        0x20.toByte(), 0x01.toByte(), 0x48.toByte(), 0x60.toByte(),
        0x48.toByte(), 0x02.toByte(), 0x00.toByte(), 0x32.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x78.toByte()
    ) // 2001:4860:4802:32::78

    // Bing: strict.bing.com
    val BING_IPV4: ByteArray = byteArrayOf(204.toByte(), 79.toByte(), 197.toByte(), 220.toByte()) // 204.79.197.220
    val BING_IPV6: ByteArray = byteArrayOf(
        0x26.toByte(), 0x20.toByte(), 0x01.toByte(), 0xec.toByte(),
        0x0c.toByte(), 0x11.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte()
    ) // 2620:1ec:c11::200

    // YouTube: restrict.youtube.com
    val YOUTUBE_IPV4: ByteArray = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 119.toByte()) // 216.239.38.119
    val YOUTUBE_IPV6: ByteArray = byteArrayOf(
        0x20.toByte(), 0x01.toByte(), 0x48.toByte(), 0x60.toByte(),
        0x48.toByte(), 0x02.toByte(), 0x00.toByte(), 0x32.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x77.toByte()
    ) // 2001:4860:4802:32::77

    // DuckDuckGo: safe.duckduckgo.com
    val DUCKDUCKGO_IPV4: ByteArray = byteArrayOf(52.toByte(), 142.toByte(), 124.toByte(), 215.toByte()) // 52.142.124.215

    private val GOOGLE_DOMAIN_REGEX = Regex(
        "^([a-z0-9-]+\\.)?google\\.(com|org|net|[a-z]{2}|com?\\.[a-z]{2}|co\\.[a-z]{2})$"
    )

    private val YOUTUBE_DOMAINS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com",
        "youtubei.googleapis.com", "youtube-nocookie.com", "www.youtube-nocookie.com"
    )

    private val BING_DOMAINS = setOf(
        "bing.com", "www.bing.com", "cn.bing.com"
    )

    private val DUCKDUCKGO_DOMAINS = setOf(
        "duckduckgo.com", "www.duckduckgo.com", "safe.duckduckgo.com"
    )

    /**
     * Checks if a domain is a search engine and returns the forced SafeSearch IP if applicable.
     * Returns null if the domain is not subject to SafeSearch enforcement.
     */
    fun getSafeSearchIp(rawDomain: String, isIpv6: Boolean): ByteArray? {
        val domain = rawDomain.trim().lowercase(Locale.ROOT).trimEnd('.')

        // 1. YouTube
        if (YOUTUBE_DOMAINS.contains(domain)) {
            return if (isIpv6) YOUTUBE_IPV6 else YOUTUBE_IPV4
        }

        // 2. Google
        if (GOOGLE_DOMAIN_REGEX.matches(domain)) {
            return if (isIpv6) GOOGLE_IPV6 else GOOGLE_IPV4
        }

        // 3. Bing
        if (BING_DOMAINS.contains(domain)) {
            return if (isIpv6) BING_IPV6 else BING_IPV4
        }

        // 4. DuckDuckGo (IPv4 only; for IPv6 returns null to force fallback or empty)
        if (DUCKDUCKGO_DOMAINS.contains(domain)) {
            return if (!isIpv6) DUCKDUCKGO_IPV4 else null
        }

        return null
    }
}
