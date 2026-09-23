package core.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class IdTokenResultTest {
    @Test
    fun `session invalid codes are classified as SessionInvalid`() {
        listOf(
            "auth/user-token-expired",
            "auth/invalid-user-token",
            "auth/user-disabled",
            "auth/user-not-found",
        ).forEach { code ->
            assertEquals(IdTokenResult.SessionInvalid(code), IdTokenResult.failureOf(code))
        }
    }

    @Test
    fun `other codes are classified as TransientFailure`() {
        // 通信断・タイムアウト・レート制限・原因不明は、セッションが有効な可能性があるためサインアウトしない
        listOf(
            "auth/network-request-failed",
            "auth/timeout",
            "auth/too-many-requests",
            "auth/internal-error",
            "unknown",
        ).forEach { code ->
            assertEquals(IdTokenResult.TransientFailure(code), IdTokenResult.failureOf(code))
        }
    }
}
