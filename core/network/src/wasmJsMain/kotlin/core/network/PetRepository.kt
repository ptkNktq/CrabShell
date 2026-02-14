package core.network

import io.ktor.client.call.*
import io.ktor.client.request.*
import model.Pet

object PetRepository {
    suspend fun getPets(): List<Pet> = authenticatedClient.get("/api/pets").body()
}
