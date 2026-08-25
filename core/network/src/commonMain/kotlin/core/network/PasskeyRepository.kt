package core.network

import model.PasskeyCredentialInfo
import model.PasskeyStatusResponse

interface PasskeyRepository {
    suspend fun getPasskeyStatus(): Result<PasskeyStatusResponse>

    suspend fun registerPasskey(displayName: String? = null): Result<Unit>

    suspend fun authenticateWithPasskey(): Result<String>

    suspend fun getPasskeyCredentials(): Result<List<PasskeyCredentialInfo>>

    suspend fun deletePasskey(id: Long): Result<Unit>
}
