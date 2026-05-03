package server.geo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpClassifierTest {
    @Test
    fun `loopback v4 is private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("127.0.0.1"))
    }

    @Test
    fun `loopback v6 is private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("::1"))
    }

    @Test
    fun `RFC1918 ranges are private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("10.0.0.1"))
        assertTrue(IpClassifier.isPrivateOrLoopback("172.16.0.1"))
        assertTrue(IpClassifier.isPrivateOrLoopback("172.31.255.255"))
        assertTrue(IpClassifier.isPrivateOrLoopback("192.168.1.1"))
    }

    @Test
    fun `CGNAT range is private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("100.64.0.1"))
        assertTrue(IpClassifier.isPrivateOrLoopback("100.127.255.255"))
    }

    @Test
    fun `link local v4 is private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("169.254.1.1"))
    }

    @Test
    fun `link local v6 is private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("fe80::1"))
        assertTrue(IpClassifier.isPrivateOrLoopback("fe80::1%eth0"))
    }

    @Test
    fun `IPv6 ULA is private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("fc00::1"))
        assertTrue(IpClassifier.isPrivateOrLoopback("fd12:3456:789a::1"))
    }

    @Test
    fun `null or blank is treated as private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback(null))
        assertTrue(IpClassifier.isPrivateOrLoopback(""))
        assertTrue(IpClassifier.isPrivateOrLoopback("   "))
    }

    @Test
    fun `invalid input is treated as private`() {
        assertTrue(IpClassifier.isPrivateOrLoopback("not-an-ip"))
        assertTrue(IpClassifier.isPrivateOrLoopback("999.999.999.999"))
    }

    @Test
    fun `public v4 is not private`() {
        assertFalse(IpClassifier.isPrivateOrLoopback("8.8.8.8"))
        assertFalse(IpClassifier.isPrivateOrLoopback("1.1.1.1"))
        assertFalse(IpClassifier.isPrivateOrLoopback("100.63.255.255"))
        assertFalse(IpClassifier.isPrivateOrLoopback("100.128.0.0"))
    }

    @Test
    fun `public v6 is not private`() {
        assertFalse(IpClassifier.isPrivateOrLoopback("2001:4860:4860::8888"))
    }
}
