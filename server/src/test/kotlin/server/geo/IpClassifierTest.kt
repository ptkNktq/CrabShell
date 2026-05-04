package server.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IpClassifierTest {
    @Test
    fun `loopback v4 is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("127.0.0.1"))
    }

    @Test
    fun `loopback v6 is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("::1"))
    }

    @Test
    fun `RFC1918 ranges are rejected`() {
        assertNull(IpClassifier.parsePublicAddress("10.0.0.1"))
        assertNull(IpClassifier.parsePublicAddress("172.16.0.1"))
        assertNull(IpClassifier.parsePublicAddress("172.31.255.255"))
        assertNull(IpClassifier.parsePublicAddress("192.168.1.1"))
    }

    @Test
    fun `CGNAT range is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("100.64.0.1"))
        assertNull(IpClassifier.parsePublicAddress("100.127.255.255"))
    }

    @Test
    fun `any-local addresses are rejected`() {
        assertNull(IpClassifier.parsePublicAddress("0.0.0.0"))
        assertNull(IpClassifier.parsePublicAddress("::"))
    }

    @Test
    fun `link local v4 is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("169.254.1.1"))
    }

    @Test
    fun `link local v6 is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("fe80::1"))
        assertNull(IpClassifier.parsePublicAddress("fe80::1%eth0"))
    }

    @Test
    fun `IPv6 ULA is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("fc00::1"))
        assertNull(IpClassifier.parsePublicAddress("fd12:3456:789a::1"))
    }

    @Test
    fun `null or blank is rejected`() {
        assertNull(IpClassifier.parsePublicAddress(null))
        assertNull(IpClassifier.parsePublicAddress(""))
        assertNull(IpClassifier.parsePublicAddress("   "))
    }

    @Test
    fun `non-literal hostname is rejected without DNS lookup`() {
        // "not-an-ip" / "example.com" は IP リテラルでないため looksLikeIpLiteral 段階で弾かれ、
        // InetAddress.getByName による DNS ルックアップは走らない。
        assertNull(IpClassifier.parsePublicAddress("not-an-ip"))
        assertNull(IpClassifier.parsePublicAddress("example.com"))
    }

    @Test
    fun `malformed IPv4 literal is rejected`() {
        assertNull(IpClassifier.parsePublicAddress("999.999.999.999"))
        assertNull(IpClassifier.parsePublicAddress("1.2.3"))
        assertNull(IpClassifier.parsePublicAddress("1.2.3.4.5"))
    }

    @Test
    fun `public v4 is accepted and parsed`() {
        assertEquals("8.8.8.8", IpClassifier.parsePublicAddress("8.8.8.8")?.hostAddress)
        assertNotNull(IpClassifier.parsePublicAddress("1.1.1.1"))
        // CGNAT 境界の外側
        assertNotNull(IpClassifier.parsePublicAddress("100.63.255.255"))
        assertNotNull(IpClassifier.parsePublicAddress("100.128.0.0"))
    }

    @Test
    fun `public v6 is accepted and parsed`() {
        assertNotNull(IpClassifier.parsePublicAddress("2001:4860:4860::8888"))
    }

    @Test
    fun `IPv4-mapped IPv6 private literal is rejected`() {
        // ::ffff:a.b.c.d 形式は InetAddress により Inet4Address に正規化されるため、
        // 配下の RFC1918 / loopback 判定が機能して弾かれることを保証する。
        assertNull(IpClassifier.parsePublicAddress("::ffff:192.168.1.1"))
        assertNull(IpClassifier.parsePublicAddress("::ffff:127.0.0.1"))
        assertNull(IpClassifier.parsePublicAddress("::ffff:10.0.0.1"))
    }

    @Test
    fun `IPv4-mapped IPv6 public literal is accepted and normalized to v4`() {
        // ::ffff:8.8.8.8 は Inet4Address に正規化され、hostAddress は IPv4 表記になる。
        val parsed = IpClassifier.parsePublicAddress("::ffff:8.8.8.8")
        assertNotNull(parsed)
        assertEquals("8.8.8.8", parsed.hostAddress)
    }
}
