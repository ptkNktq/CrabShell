package server.geo

import java.net.InetAddress

/**
 * 信頼できない経路（X-Forwarded-For など）から渡された IP 文字列を安全に [InetAddress] に変換し、
 * GeoIP 解決の対象としてふさわしいかを判定する。
 *
 * 除外対象（[parsePublicAddress] が null を返すケース）:
 * - null / blank / IP リテラルでない文字列（ホスト名等。DNS ルックアップを誘発しないように事前に弾く）
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
    /**
     * GeoIP 解決対象としてふさわしい IP なら parse 済み [InetAddress] を返す。
     * それ以外（不正入力 / プライベート / ループバック等）は null。
     *
     * IP リテラルでない（ホスト名のような）入力は [InetAddress.getByName] による DNS ルックアップを
     * 誘発するため、先に [looksLikeIpLiteral] で弾く。
     */
    fun parsePublicAddress(ip: String?): InetAddress? {
        if (ip.isNullOrBlank()) return null
        // IPv6 zone identifier（"fe80::1%eth0"）を除去
        val normalized = ip.substringBefore('%')
        if (!looksLikeIpLiteral(normalized)) return null
        val addr = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return null
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isInCgnatRange() ||
            addr.isInIpv6UniqueLocalRange()
        ) {
            return null
        }
        return addr
    }

    /**
     * 数値 IP リテラルらしき文字列か判定する。`InetAddress.getByName` にホスト名を渡すと
     * DNS ルックアップ（ブロッキング I/O）が走るため、その手前で文字種を絞る。
     * - IPv6: `:` を含み、かつ hex / `:` / `.`(IPv4-mapped) のみ
     * - IPv4: `.` のみで区切られ、digit と `.` のみ、ドットは 3 個
     */
    private fun looksLikeIpLiteral(s: String): Boolean {
        if (s.isEmpty()) return false
        if (':' in s) {
            return s.all { it == ':' || it == '.' || it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }
        if (s.all { it == '.' || it in '0'..'9' }) {
            return s.count { it == '.' } == 3
        }
        return false
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
