package server.auth

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserRecord
import model.User
import org.slf4j.LoggerFactory

/** Firebase Admin SDK による [FirebaseAuthRepository] の実装 */
class FirebaseAdminAuthRepository(
    private val firebaseAuth: FirebaseAuth,
) : FirebaseAuthRepository {
    private val logger = LoggerFactory.getLogger(FirebaseAdminAuthRepository::class.java)

    override fun verifyIdToken(idToken: String): FirebaseToken? =
        try {
            firebaseAuth.verifyIdToken(idToken)
        } catch (e: Exception) {
            logger.warn("Firebase token verification failed", e)
            null
        }

    override fun getUserStatus(uid: String): FirebaseUserStatus =
        try {
            val user = firebaseAuth.getUser(uid)
            if (user.isDisabled) FirebaseUserStatus.DISABLED else FirebaseUserStatus.ACTIVE
        } catch (e: FirebaseAuthException) {
            if (e.authErrorCode == AuthErrorCode.USER_NOT_FOUND) FirebaseUserStatus.NOT_FOUND else throw e
        }

    override fun getDisplayName(uid: String): String? =
        try {
            firebaseAuth
                .getUser(uid)
                .displayName
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger.warn("Failed to get displayName for uid={}", uid, e)
            null
        }

    override fun listUsers(): List<User> {
        val users = mutableListOf<User>()
        var page = firebaseAuth.listUsers(null)
        while (page != null) {
            page.values.mapTo(users) { it.toUser() }
            page = page.nextPage
        }
        return users
    }

    override fun updateDisplayName(
        uid: String,
        displayName: String,
    ): User = firebaseAuth.updateUser(UserRecord.UpdateRequest(uid).setDisplayName(displayName)).toUser()

    override fun createCustomToken(uid: String): String? =
        try {
            firebaseAuth.createCustomToken(uid)
        } catch (e: Exception) {
            logger.warn("Failed to create custom token for uid={}", uid, e)
            null
        }

    private fun UserRecord.toUser(): User =
        User(
            uid = uid,
            email = email ?: "",
            displayName = displayName,
            isAdmin = customClaims["admin"] == true,
        )
}
