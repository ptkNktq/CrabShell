package server.passkey

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * WebAuthn チャレンジの一時保持。
 * キーはユーザー識別子（登録時は firebaseUid、認証時はメールアドレス）。
 * 5分 TTL で自動削除。
 */
object ChallengeStore {
    private val store = ConcurrentHashMap<String, ChallengeEntry>()
    private val random = SecureRandom()
    private const val TTL_MS = 5 * 60 * 1000L // 5 分

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

    private fun cleanup() {
        val now = System.currentTimeMillis()
        store.entries.removeIf { now - it.value.createdAt > TTL_MS }
    }
}
