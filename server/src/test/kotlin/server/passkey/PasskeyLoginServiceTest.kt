package server.passkey

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import server.auth.FirebaseUserDirectory
import server.auth.FirebaseUserStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PasskeyLoginServiceTest {
    private val userDirectory = mockk<FirebaseUserDirectory>()
    private val credentialStore = mockk<PasskeyCredentialStore>()
    private val service = PasskeyLoginService(userDirectory, credentialStore)

    private val uid = "user1"

    @Test
    fun activeUserGetsCustomToken() {
        every { userDirectory.getUserStatus(uid) } returns FirebaseUserStatus.ACTIVE
        every { userDirectory.createCustomToken(uid) } returns "token"

        val result = service.issueCustomToken(uid)

        assertEquals(PasskeyLoginResult.Success("token"), result)
        verify(exactly = 0) { credentialStore.deleteCredentials(any()) }
    }

    @Test
    fun nonExistentUserIsRejectedAndCredentialsAreDeleted() {
        every { userDirectory.getUserStatus(uid) } returns FirebaseUserStatus.NOT_FOUND
        every { credentialStore.deleteCredentials(uid) } returns 1

        val result = service.issueCustomToken(uid)

        assertEquals(PasskeyLoginResult.Rejected, result)
        verify(exactly = 0) { userDirectory.createCustomToken(any()) }
        verify(exactly = 1) { credentialStore.deleteCredentials(uid) }
    }

    @Test
    fun disabledUserIsRejectedWithoutDeletingCredentials() {
        every { userDirectory.getUserStatus(uid) } returns FirebaseUserStatus.DISABLED

        val result = service.issueCustomToken(uid)

        assertEquals(PasskeyLoginResult.Rejected, result)
        verify(exactly = 0) { userDirectory.createCustomToken(any()) }
        verify(exactly = 0) { credentialStore.deleteCredentials(any()) }
    }

    @Test
    fun userStatusLookupFailureIsUnavailableWithoutDeletingCredentials() {
        every { userDirectory.getUserStatus(uid) } throws RuntimeException("network error")

        val result = service.issueCustomToken(uid)

        assertEquals(PasskeyLoginResult.Unavailable, result)
        verify(exactly = 0) { userDirectory.createCustomToken(any()) }
        verify(exactly = 0) { credentialStore.deleteCredentials(any()) }
    }

    @Test
    fun customTokenCreationFailureIsUnavailable() {
        every { userDirectory.getUserStatus(uid) } returns FirebaseUserStatus.ACTIVE
        every { userDirectory.createCustomToken(uid) } returns null

        val result = service.issueCustomToken(uid)

        assertEquals(PasskeyLoginResult.Unavailable, result)
    }
}
