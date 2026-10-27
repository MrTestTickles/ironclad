package io.github.ironclad.dns

/**
 * Detects and classifies DNS-bypass attempts at the IP/port level.
 *
 * Bypass vectors covered:
 *  - DoT  (DNS-over-TLS)  : port 853 TCP/UDP to any destination
 *  - DoH  (DNS-over-HTTPS): port 443 TCP to known DoH server IPs
 *  - Plain DNS to rogue IPs: any port-53 packet NOT to our virtual DNS
 *
 * The VPN routes known DoH IPs into the tunnel (via addRoute in
 * IronVpnService), so packets headed to these IPs arrive here and we can drop them.
 */
object BypassBlocker {

    const val PORT_DNS = 53
    const val PORT_DOT = 853   // DNS-over-TLS
    const val PORT_DOH = 443   // DNS-over-HTTPS

    val DOH_SERVER_IPS: Set<String> = setOf(
        // Cloudflare
        "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3",
        "2606:4700:4700::1111", "2606:4700:4700::1001",
        // Google
        "8.8.8.8", "8.8.4.4",
        "2001:4860:4860::8888", "2001:4860:4860::8844",
        // Quad9
        "9.9.9.9", "149.112.112.112", "9.9.9.10", "149.112.112.10", "9.9.9.11", "149.112.112.11",
        "2620:fe::fe", "2620:fe::9", "2620:fe::11",
        // CleanBrowsing
        "185.228.168.9", "185.228.169.9",
        "185.228.168.10", "185.228.169.10",
        "185.228.168.168", "185.228.169.168",
        // AdGuard
        "94.140.14.14", "94.140.15.15",
        "94.140.14.15", "94.140.15.16",
        "94.140.14.140", "94.140.14.141",
        "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
        // NextDNS
        "45.90.28.0", "45.90.29.0", "45.90.30.0", "45.90.31.0",
        // OpenDNS
        "208.67.222.222", "208.67.220.220",
        "208.67.222.123", "208.67.220.123",
        // Mullvad
        "194.242.2.2", "194.242.2.3", "194.242.2.4", "194.242.2.5", "194.242.2.9",
        "2a07:e340::2",
        // Control D
        "76.76.2.0", "76.76.10.0", "76.76.2.1", "76.76.10.1", "76.76.2.2", "76.76.10.2",
        "2606:1a40::", "2606:1a40:1::",
        // Comodo
        "8.26.56.26", "8.20.247.20",
        // Level3
        "4.2.2.1", "4.2.2.2", "4.2.2.3", "4.2.2.4", "4.2.2.5", "4.2.2.6",
        // Verisign
        "64.6.64.6", "64.6.65.6",
        // dns.sb
        "185.222.222.222", "45.11.45.11",
        // Yandex
        "77.88.8.8", "77.88.8.1",
        // Alibaba / AliDNS
        "223.5.5.5", "223.6.6.6",
        // Baidu
        "180.76.76.76",
        // Tencent / DNSPod
        "119.29.29.29",
        // 114DNS
        "114.114.114.114", "114.114.115.115",
        // Hurricane Electric
        "74.82.42.42", "2001:470:20::2",
        // CIRA Canadian Shield
        "149.112.121.10", "149.112.122.10",
        // Gcore
        "95.85.95.85", "2.56.220.2",
        // LibreDNS & Applied Privacy
        "116.202.176.26", "146.255.56.98"
    )

    /**
     * Returns the bypass type if this packet should be silently dropped, or null if it's clean.
     *
     * @param protocol  IP protocol byte (6=TCP, 17=UDP)
     * @param dstPort   Destination port
     * @param dstIpStr  Destination IP as string
     */
    fun classify(protocol: Int, dstPort: Int, dstIpStr: String): BypassType? = when {
        // DoT: any UDP/TCP to port 853
        dstPort == PORT_DOT -> BypassType.DNS_OVER_TLS

        // DoH: TCP port 443 to a known resolver IP
        protocol == PROTO_TCP && dstPort == PORT_DOH && DOH_SERVER_IPS.contains(dstIpStr) ->
            BypassType.DNS_OVER_HTTPS

        // Rogue plain DNS: UDP port 53 to a known resolver IP other than our virtual DNS
        protocol == PROTO_UDP && dstPort == PORT_DNS && DOH_SERVER_IPS.contains(dstIpStr) ->
            BypassType.ROGUE_DNS

        else -> null
    }

    enum class BypassType { DNS_OVER_TLS, DNS_OVER_HTTPS, ROGUE_DNS }

    const val PROTO_TCP = 6
    const val PROTO_UDP = 17
}
