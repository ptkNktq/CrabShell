package server.passkey

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import server.auth.FirebaseAuthRepository
import server.auth.FirebaseUserStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PasskeyLoginServiceTest {
    private val firebaseAuthRepository = mockk<FirebaseAuthRepository>()
    private val credentialRepository = mockk<PasskeyCredentialRepository>()
    private val service = PasskeyLoginService(firebaseAuthRepository, credentialRepository)

    private val uid = "user1"

    @Test
    fun activeUserGetsCustomToken() {
        every { firebaseAuthRepository.getUserStatus(uid) } returns FirebaseUserStatus.ACTIVE
        every { firebaseAuthRepository.createCustomToken(uid) } returns "token"

        val result = service.authorizeLogin(uid)

        assertEquals(PasskeyLoginResult.Success("token"), result)
        verify(exactly = 0) { credentialRepository.deleteByUid(any()) }
    }

    @Test
    fun nonExistentUserIsRejectedAndCredentialsAreDeleted() {
        every { firebaseAuthRepository.getUserStatus(uid) } returns FirebaseUserStatus.NOT_FOUND
        every { credentialRepository.deleteByUid(uid) } returns 1

        val result = service.authorizeLogin(uid)

        assertEquals(PasskeyLoginResult.Rejected, result)
        verify(exactly = 0) { firebaseAuthRepository.createCustomToken(any()) }
        verify(exactly = 1) { credentialRepository.deleteByUid(uid) }
    }

    @Test
    fun nonExistentUserIsRejectedEvenIfCredentialDeletionFails() {
        every { firebaseAuthRepository.getUserStatus(uid) } returns FirebaseUserStatus.NOT_FOUND
        every { credentialRepository.deleteByUid(uid) } throws RuntimeException("db error")

        val result = service.authorizeLogin(uid)

        assertEquals(PasskeyLoginResult.Rejected, result)
        verify(exactly = 0) { firebaseAuthRepository.createCustomToken(any()) }
        verify(exactly = 1) { credentialRepository.deleteByUid(uid) }
    }

    @Test
    fun disabledUserIsRejectedWithoutDeletingCredentials() {
        every { firebaseAuthRepository.getUserStatus(uid) } returns FirebaseUserStatus.DISABLED

        val result = service.authorizeLogin(uid)

        assertEquals(PasskeyLoginResult.Rejected, result)
        verify(exactly = 0) { firebaseAuthRepository.createCustomToken(any()) }
        verify(exactly = 0) { credentialRepository.deleteByUid(any()) }
    }

    @Test
    fun userStatusLookupFailureIsUnavailableWithoutDeletingCredentials() {
        every { firebaseAuthRepository.getUserStatus(uid) } throws RuntimeException("network error")

        val result = service.authorizeLogin(uid)

        assertEquals(PasskeyLoginResult.Unavailable, result)
        verify(exactly = 0) { firebaseAuthRepository.createCustomToken(any()) }
        verify(exactly = 0) { credentialRepository.deleteByUid(any()) }
    }

    @Test
    fun customTokenCreationFailureIsUnavailable() {
        every { firebaseAuthRepository.getUserStatus(uid) } returns FirebaseUserStatus.ACTIVE
        every { firebaseAuthRepository.createCustomToken(uid) } returns null

        val result = service.authorizeLogin(uid)

        assertEquals(PasskeyLoginResult.Unavailable, result)
        verify(exactly = 0) { credentialRepository.deleteByUid(any()) }
    }
}
