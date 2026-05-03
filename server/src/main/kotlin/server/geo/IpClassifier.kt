package server.geo

import java.net.InetAddress

/**
 * GeoIP 解決対象から除外すべき IP アドレスを判定する。
 *
 * 除外対象:
 * - ループバック（127.0.0.0/8, ::1）
 * - リンクローカル（169.254.0.0/16, fe80::/10）
 * - RFC1918 プライベート（10/8, 172.16/12, 192.168/16）
 * - CGNAT（100.64.0.0/10）
 * - IPv6 ULA（fc00::/7）
 * - any-local（0.0.0.0, ::）
 *
 * MaxMind の DB はパブリック IP しか含まないため、これらを問い合わせても無駄なルックアップが
 * 走り [com.maxmind.geoip2.exception.AddressNotFoundException] が出るだけ。先に弾く。
 */
object IpClassifier {
    fun isPrivateOrLoopback(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return true
        // IPv6 zone identifier（"fe80::1%eth0"）を除去
        val normalized = ip.substringBefore('%')
        return runCatching {
            val addr = InetAddress.getByName(normalized)
            addr.isAnyLocalAddress ||
                addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isInCgnatRange() ||
                addr.isInIpv6UniqueLocalRange()
        }.getOrDefault(true)
    }

    /** RFC6598 100.64.0.0/10 (CGNAT) は [InetAddress.isSiteLocalAddress] では検出されないため自前で判定。 */
    private fun InetAddress.isInCgnatRange(): Boolean {
        val bytes = address
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        return first == 100 && second in 64..127
    }

    /**
     * RFC4193 IPv6 Unique Local Address (fc00::/7) を判定。
     * Java の [InetAddress.isSiteLocalAddress] は廃止済みの fec0::/10 しかチェックしないため自前で判定する。
     */
    private fun InetAddress.isInIpv6UniqueLocalRange(): Boolean {
        val bytes = address
        if (bytes.size != 16) return false
        return (bytes[0].toInt() and 0xFE) == 0xFC
    }
}
