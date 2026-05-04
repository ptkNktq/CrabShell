package feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginHistoryFormatTest {
    @Test
    fun `all three fields joined city first`() {
        assertEquals(
            "千代田区, 東京都, 日本",
            formatLocation(country = "日本", region = "東京都", city = "千代田区"),
        )
    }

    @Test
    fun `region equal to city is deduplicated`() {
        assertEquals(
            "東京, 日本",
            formatLocation(country = "日本", region = "東京", city = "東京"),
        )
    }

    @Test
    fun `null country is omitted`() {
        assertEquals(
            "千代田区, 東京都",
            formatLocation(country = null, region = "東京都", city = "千代田区"),
        )
    }

    @Test
    fun `only country present`() {
        assertEquals(
            "日本",
            formatLocation(country = "日本", region = null, city = null),
        )
    }

    @Test
    fun `all null returns null`() {
        assertNull(formatLocation(null, null, null))
    }

    @Test
    fun `blank strings are treated as missing`() {
        assertNull(formatLocation("", "  ", ""))
    }
}
