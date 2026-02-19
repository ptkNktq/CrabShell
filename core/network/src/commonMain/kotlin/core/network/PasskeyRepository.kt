package core.network

import model.PasskeyStatusResponse

interface PasskeyRepository {
    suspend fun getPasskeyStatus(): Result<PasskeyStatusResponse>

    suspend fun registerPasskey(displayName: String? = null): Result<Unit>

    suspend fun authenticateWithPasskey(email: String): Result<String>
}
