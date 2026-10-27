package io.github.ironclad.dns

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * High-performance adult content and DNS-bypass filter.
 *
 * Two-tier matching:
 *  1. Fast in-memory HashSet loaded from bundled blocklist.txt.gz (~1.2 M domains)
 *  2. Hardcoded TLD, keyword, and DoH bypass fallbacks that fire even before
 *     the asset is loaded (or if loading fails).
 *
 * Call [initialize] once at VPN startup (off the main thread).
 */
object AdultContentFilter {

    // ---- Tier-2 fallbacks (always active, zero I/O) -------------------------

    private val BLOCKED_TLDS = setOf("xxx", "adult", "porn", "sex")

    val DOH_BYPASS_DOMAINS = setOf(
        "cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com",
        "dns.google", "dns.google.com", "dns64.dns.google",
        "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
        "dns.adguard.com", "dns.adguard-dns.com",
        "doh.opendns.com", "doh.familyshield.opendns.com",
        "dns.nextdns.io",
        "doh.cleanbrowsing.org",
        "doh.mullvad.net",
        "dns.controld.com",
        "dns.sb", "doh.dns.sb",
        "dns.alidns.com",
        "doh.pub", "dot.pub",
        "doh.crypto.sx",
        "doh.tiar.app",
        "dns.umbrella.com"
    )

    // Keywords checked against the domain body (everything except the TLD)
    private val BLOCKED_KEYWORDS = listOf(
        "porn", "hentai", "erotic", "erotik", "nudist", "camgirl",
        "onlyfans", "chaturbate", "pornhub", "xvideos", "xhamster",
        "redtube", "spankbang", "eporner", "bongacams", "camsoda",
        "livejasmin", "stripchat", "camwhores", "thumbzilla"
    )

    // Hardcoded high-impact domains as immediate fallback (loaded before asset)
    private val SEED_DOMAINS = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com",
        "redtube.com", "youporn.com", "chaturbate.com", "onlyfans.com",
        "spankbang.com", "tube8.com", "porn.com", "cam4.com", "beeg.com",
        "eporner.com", "motherless.com", "rule34.xxx", "gelbooru.com",
        "e-hentai.org", "nhentai.net", "hanime.tv", "stripchat.com",
        "livejasmin.com", "camsoda.com", "bongacams.com", "myfreecams.com",
        "brazzers.com", "daftsex.com", "txxx.com", "hqporner.com",
        "erome.com", "fetlife.com", "adultfriendfinder.com", "fapello.com",
        "coomer.party", "kemono.party", "hitomi.la", "pururin.io"
    )

    // ---- Tier-1: asset-backed HashSet (populated by initialize()) ----------

    @Volatile
    private var assetDomains: HashSet<String>? = null

    /**
     * Load the gzipped blocklist asset into memory.
     * Call once from a background thread (e.g. in IronVpnService.onCreate).
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun initialize(context: Context) {
        if (assetDomains != null) return
        try {
            val set = HashSet<String>(1_400_000)
            context.assets.open("blocklist.txt.gz").use { raw ->
                GZIPInputStream(raw).use { gz ->
                    BufferedReader(InputStreamReader(gz)).use { reader ->
                        reader.forEachLine { line ->
                            val domain = line.trim()
                            if (domain.isNotEmpty() && !domain.startsWith('#')) {
                                set.add(domain)
                            }
                        }
                    }
                }
            }
            assetDomains = set
        } catch (e: Exception) {
            // ponytail: silent fallback to SEED_DOMAINS + keyword matching if asset load fails
            assetDomains = HashSet(SEED_DOMAINS)
        }
    }

    // ---- Public API ---------------------------------------------------------

    /**
     * Returns true if [rawDomain] should be sinkholed to 0.0.0.0.
     */
    fun isBlocked(rawDomain: String): Boolean {
        val domain = normalizeDomain(rawDomain)
        if (domain.isEmpty()) return false

        // DoH bypass check with parent-domain walk (e.g. *.cloudflare-dns.com, *.dns.nextdns.io)
        var parentCheck: String? = domain
        while (parentCheck != null) {
            if (DOH_BYPASS_DOMAINS.contains(parentCheck)) return true
            val dot = parentCheck.indexOf('.')
            parentCheck = if (dot in 0 until parentCheck.length - 1) parentCheck.substring(dot + 1) else null
        }

        // TLD check
        val parts = domain.split('.')
        if (parts.size >= 2 && BLOCKED_TLDS.contains(parts.last())) return true

        // Tier-1: HashSet lookup (exact + parent-domain walk)
        val loaded = assetDomains
        if (loaded != null) {
            var current: String? = domain
            while (current != null) {
                if (loaded.contains(current)) return true
                val dot = current.indexOf('.')
                current = if (dot in 0 until current.length - 1) current.substring(dot + 1) else null
            }
        } else {
            // Asset not loaded yet — seed fallback
            var current: String? = domain
            while (current != null) {
                if (SEED_DOMAINS.contains(current)) return true
                val dot = current.indexOf('.')
                current = if (dot in 0 until current.length - 1) current.substring(dot + 1) else null
            }
        }

        // Keyword check on domain body (excluding TLD)
        val body = if (parts.size >= 2) parts.dropLast(1).joinToString(".") else domain
        return BLOCKED_KEYWORDS.any { body.contains(it) }
    }

    fun normalizeDomain(domain: String): String =
        domain.trim().lowercase(Locale.ROOT).trimEnd('.')
}
