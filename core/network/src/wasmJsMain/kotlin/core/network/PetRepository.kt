package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import model.Pet

interface PetRepository {
    suspend fun getPets(): List<Pet>
}

class PetRepositoryImpl(private val client: HttpClient) : PetRepository {
    override suspend fun getPets(): List<Pet> = client.get("/api/pets").body()
}
