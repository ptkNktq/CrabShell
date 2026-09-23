package feature.auth

import core.auth.AuthState
import model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AuthStateScopeKeyTest {
    private fun authenticated(
        uid: String,
        isAdmin: Boolean = false,
    ) = AuthState.Authenticated(User(uid = uid, email = "$uid@example.com", isAdmin = isAdmin))

    @Test
    fun `each auth state kind has a distinct key`() {
        val keys =
            listOf(
                AuthState.Loading.viewModelScopeKey(),
                AuthState.Unauthenticated.viewModelScopeKey(),
                authenticated("user-a").viewModelScopeKey(),
            )

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `re-created Authenticated with same uid keeps the key`() {
        // トークンリフレッシュで isAdmin 等が更新された Authenticated が再生成されるケース
        val before = authenticated("user-a", isAdmin = false)
        val after = authenticated("user-a", isAdmin = true)

        assertEquals(before.viewModelScopeKey(), after.viewModelScopeKey())
    }

    @Test
    fun `different uid changes the key`() {
        assertNotEquals(
            authenticated("user-a").viewModelScopeKey(),
            authenticated("user-b").viewModelScopeKey(),
        )
    }
}
