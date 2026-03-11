package server.feeding

import model.MealTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class QuickFeedServiceTest {
    @Test
    fun hmacSha256ProducesDeterministicOutput() {
        val token1 = QuickFeedService.hmacSha256("secret", "pet1:2026-03-12:LUNCH")
        val token2 = QuickFeedService.hmacSha256("secret", "pet1:2026-03-12:LUNCH")
        assertEquals(token1, token2)
    }

    @Test
    fun hmacSha256DifferentInputsProduceDifferentTokens() {
        val token1 = QuickFeedService.hmacSha256("secret", "pet1:2026-03-12:LUNCH")
        val token2 = QuickFeedService.hmacSha256("secret", "pet1:2026-03-12:EVENING")
        assertNotEquals(token1, token2)
    }

    @Test
    fun hmacSha256DifferentKeysProduceDifferentTokens() {
        val token1 = QuickFeedService.hmacSha256("key1", "pet1:2026-03-12:LUNCH")
        val token2 = QuickFeedService.hmacSha256("key2", "pet1:2026-03-12:LUNCH")
        assertNotEquals(token1, token2)
    }

    @Test
    fun hmacSha256OutputIsHexString() {
        val token = QuickFeedService.hmacSha256("secret", "data")
        assertNotNull(token)
        // SHA-256 は 32 バイト = 64 hex 文字
        assertEquals(64, token.length)
        assert(token.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Token should be lowercase hex: $token"
        }
    }

    @Test
    fun hmacSha256AllMealTimesProduceDifferentTokens() {
        val tokens =
            MealTime.entries.map { meal ->
                QuickFeedService.hmacSha256("secret", "pet1:2026-03-12:${meal.name}")
            }
        assertEquals(tokens.size, tokens.toSet().size, "All meal time tokens should be unique")
    }
}
