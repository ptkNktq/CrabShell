package core.network

import core.auth.AuthRepository
import core.auth.IdTokenResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthHttpClientTest {
    /** getIdToken の呼び出し（forceRefresh の有無）を記録し、設定した結果を返すフェイク。 */
    private class FakeAuthRepository(
        private val results: (forceRefresh: Boolean) -> IdTokenResult,
    ) : AuthRepository {
        val getIdTokenCalls = mutableListOf<Boolean>()
        var signOutCount = 0

        override suspend fun getIdToken(forceRefresh: Boolean): IdTokenResult {
            getIdTokenCalls += forceRefresh
            return results(forceRefresh)
        }

        override suspend fun signOut(): Result<Unit> {
            signOutCount++
            return Result.success(Unit)
        }

        override fun startListening() = Unit

        override suspend fun signIn(
            email: String,
            password: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun changePassword(
            currentPassword: String,
            newPassword: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun refreshClaims() = Unit

        override suspend fun signInWithCustomToken(token: String): Result<Unit> = Result.success(Unit)

        override fun isWebAuthnSupported(): Boolean = true
    }

    /** 受け取った Authorization ヘッダを記録し、[statusFor] のステータスで応答するクライアントを作る。 */
    private fun client(
        authRepository: AuthRepository,
        sentAuthorizations: MutableList<String?>,
        statusFor: (authorization: String?) -> HttpStatusCode,
    ): HttpClient =
        HttpClient(
            MockEngine { request ->
                val authorization = request.headers[HttpHeaders.Authorization]
                sentAuthorizations += authorization
                respond(content = "ok", status = statusFor(authorization))
            },
        ) {
            configureAuthenticatedClient(authRepository)
        }

    @Test
    fun `attaches ID token fetched for each request`() =
        runTest {
            var count = 0
            val repo = FakeAuthRepository { IdTokenResult.Success("token-${++count}") }
            val sent = mutableListOf<String?>()
            val client = client(repo, sent) { HttpStatusCode.OK }

            client.get("/api/a")
            client.get("/api/b")

            // キャッシュせずリクエストごとに取得したトークンを使う
            assertEquals<List<String?>>(listOf("Bearer token-1", "Bearer token-2"), sent)
            assertEquals(listOf(false, false), repo.getIdTokenCalls)
        }

    @Test
    fun `retries once with force-refreshed token on 401`() =
        runTest {
            val repo =
                FakeAuthRepository { force ->
                    IdTokenResult.Success(if (force) "fresh" else "stale")
                }
            val sent = mutableListOf<String?>()
            val client =
                client(repo, sent) { auth ->
                    if (auth == "Bearer fresh") HttpStatusCode.OK else HttpStatusCode.Unauthorized
                }

            val body = client.get("/api/a").bodyAsText()

            assertEquals("ok", body)
            assertEquals<List<String?>>(listOf("Bearer stale", "Bearer fresh"), sent)
            assertEquals(listOf(false, true), repo.getIdTokenCalls)
            assertEquals(0, repo.signOutCount)
        }

    @Test
    fun `signs out when refreshed token is still rejected`() =
        runTest {
            val repo = FakeAuthRepository { IdTokenResult.Success("token") }
            val sent = mutableListOf<String?>()
            val client = client(repo, sent) { HttpStatusCode.Unauthorized }

            assertFailsWith<AuthSessionExpiredException> { client.get("/api/a") }
            assertEquals(2, sent.size)
            assertEquals(1, repo.signOutCount)
        }

    @Test
    fun `does not sign out when token refresh fails transiently`() =
        runTest {
            // スリープ明け等で強制更新が通信断により失敗したケース
            val repo =
                FakeAuthRepository { force ->
                    if (force) {
                        IdTokenResult.TransientFailure("auth/network-request-failed")
                    } else {
                        IdTokenResult.Success("stale")
                    }
                }
            val sent = mutableListOf<String?>()
            val client = client(repo, sent) { HttpStatusCode.Unauthorized }

            val e = assertFailsWith<IdTokenUnavailableException> { client.get("/api/a") }
            assertEquals("auth/network-request-failed", e.code)
            assertEquals(0, repo.signOutCount)
        }

    @Test
    fun `does not send request nor sign out when token is unavailable`() =
        runTest {
            val repo = FakeAuthRepository { IdTokenResult.TransientFailure("auth/network-request-failed") }
            val sent = mutableListOf<String?>()
            val client = client(repo, sent) { HttpStatusCode.OK }

            assertFailsWith<IdTokenUnavailableException> { client.get("/api/a") }
            assertTrue(sent.isEmpty())
            assertEquals(0, repo.signOutCount)
        }

    @Test
    fun `signs out when session is invalid`() =
        runTest {
            val repo = FakeAuthRepository { IdTokenResult.SessionInvalid("auth/user-token-expired") }
            val sent = mutableListOf<String?>()
            val client = client(repo, sent) { HttpStatusCode.OK }

            assertFailsWith<AuthSessionExpiredException> { client.get("/api/a") }
            assertTrue(sent.isEmpty())
            assertEquals(1, repo.signOutCount)
        }

    @Test
    fun `sends without Authorization header when signed out`() =
        runTest {
            val repo = FakeAuthRepository { IdTokenResult.SignedOut }
            val sent = mutableListOf<String?>()
            val client = client(repo, sent) { HttpStatusCode.OK }

            client.get("/api/a")

            assertEquals(1, sent.size)
            assertNull(sent.single())
        }
}
