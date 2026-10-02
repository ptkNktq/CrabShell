package server.passkey

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChallengeStoreTest {
    // テストごとに新しいインスタンスを使い、テスト間で発行済みチャレンジを共有しない
    private val challengeStore = ChallengeStore()

    @Test
    fun generateReturnsChallengeOf32Bytes() {
        val challenge = challengeStore.generate("test-user")
        assertEquals(32, challenge.size)
    }

    @Test
    fun consumeReturnsChallengeAndRemovesIt() {
        challengeStore.generate("user-a")
        val consumed = challengeStore.consume("user-a")
        assertNotNull(consumed)
        assertEquals(32, consumed.size)
        // 2回目は null
        assertNull(challengeStore.consume("user-a"))
    }

    @Test
    fun consumeReturnsNullForUnknownKey() {
        assertNull(challengeStore.consume("nonexistent-key"))
    }

    @Test
    fun generateOverwritesPreviousChallenge() {
        val first = challengeStore.generate("user-b")
        val second = challengeStore.generate("user-b")
        val consumed = challengeStore.consume("user-b")
        assertNotNull(consumed)
        // 上書きされたので最後に生成した値が返る
        assertEquals(second.toList(), consumed.toList())
    }

    @Test
    fun generateAnonymousReturnsChallengeOf32Bytes() {
        val challenge = challengeStore.generateAnonymous()
        assertEquals(32, challenge.size)
    }

    @Test
    fun consumeAnonymousReturnsChallengeAndRemovesIt() {
        val challenge = challengeStore.generateAnonymous()
        val consumed = challengeStore.consumeAnonymous(challenge)
        assertNotNull(consumed)
        assertEquals(challenge.toList(), consumed.toList())
        // 2回目は null
        assertNull(challengeStore.consumeAnonymous(challenge))
    }

    @Test
    fun consumeAnonymousReturnsNullForUnissuedChallenge() {
        val neverIssued = ByteArray(32) { it.toByte() }
        assertNull(challengeStore.consumeAnonymous(neverIssued))
    }
}
