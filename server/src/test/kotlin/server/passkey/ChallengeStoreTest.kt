package server.passkey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChallengeStoreTest {
    @Test
    fun generateReturnsChallengeOf32Bytes() {
        val challenge = ChallengeStore.generate("test-user")
        assertEquals(32, challenge.size)
        // cleanup
        ChallengeStore.consume("test-user")
    }

    @Test
    fun consumeReturnsChallengeAndRemovesIt() {
        ChallengeStore.generate("user-a")
        val consumed = ChallengeStore.consume("user-a")
        assertNotNull(consumed)
        assertEquals(32, consumed.size)
        // 2回目は null
        assertNull(ChallengeStore.consume("user-a"))
    }

    @Test
    fun consumeReturnsNullForUnknownKey() {
        assertNull(ChallengeStore.consume("nonexistent-key"))
    }

    @Test
    fun generateOverwritesPreviousChallenge() {
        val first = ChallengeStore.generate("user-b")
        val second = ChallengeStore.generate("user-b")
        val consumed = ChallengeStore.consume("user-b")
        assertNotNull(consumed)
        // 上書きされたので最後に生成した値が返る
        assertEquals(second.toList(), consumed.toList())
    }

    @Test
    fun generateAnonymousReturnsChallengeOf32Bytes() {
        val challenge = ChallengeStore.generateAnonymous()
        assertEquals(32, challenge.size)
        // cleanup
        ChallengeStore.consumeAnonymous(challenge)
    }

    @Test
    fun consumeAnonymousReturnsChallengeAndRemovesIt() {
        val challenge = ChallengeStore.generateAnonymous()
        val consumed = ChallengeStore.consumeAnonymous(challenge)
        assertNotNull(consumed)
        assertEquals(challenge.toList(), consumed.toList())
        // 2回目は null
        assertNull(ChallengeStore.consumeAnonymous(challenge))
    }

    @Test
    fun consumeAnonymousReturnsNullForUnissuedChallenge() {
        val neverIssued = ByteArray(32) { it.toByte() }
        assertNull(ChallengeStore.consumeAnonymous(neverIssued))
    }
}
