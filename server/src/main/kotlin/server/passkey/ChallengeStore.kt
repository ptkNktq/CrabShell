package server.passkey

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * WebAuthn チャレンジの一時保持。
 * キーはユーザー識別子（登録時の firebaseUid）。
 * usernameless 認証（[generateAnonymous]）はユーザー識別子が事前にわからないため、
 * チャレンジ自体をキーにする。
 * 5分 TTL で自動削除。
 */
object ChallengeStore {
    private val store = ConcurrentHashMap<String, ChallengeEntry>()
    private val random = SecureRandom()
    private const val TTL_MS = 5 * 60 * 1000L // 5 分
    private const val ANONYMOUS_KEY_PREFIX = "anon:"

    data class ChallengeEntry(
        val challenge: ByteArray,
        val createdAt: Long = System.currentTimeMillis(),
    )

    fun generate(key: String): ByteArray {
        cleanup()
        val challenge = ByteArray(32).also { random.nextBytes(it) }
        store[key] = ChallengeEntry(challenge)
        return challenge
    }

    fun consume(key: String): ByteArray? {
        cleanup()
        return store.remove(key)?.challenge
    }

    /**
     * ユーザー識別子なしでチャレンジを生成する（usernameless 認証用）。
     * チャレンジ自体を発行済みの証跡としてキーに保存する。
     */
    fun generateAnonymous(): ByteArray {
        cleanup()
        val challenge = ByteArray(32).also { random.nextBytes(it) }
        store[anonymousKey(challenge)] = ChallengeEntry(challenge)
        return challenge
    }

    /**
     * [generateAnonymous] で発行したチャレンジを検証・消費する。
     * 未発行・使用済み・期限切れのいずれかであれば null を返す。
     */
    fun consumeAnonymous(challenge: ByteArray): ByteArray? {
        cleanup()
        return store.remove(anonymousKey(challenge))?.challenge
    }

    private fun anonymousKey(challenge: ByteArray): String =
        ANONYMOUS_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)

    private fun cleanup() {
        val now = System.currentTimeMillis()
        store.entries.removeIf { now - it.value.createdAt > TTL_MS }
    }
}
