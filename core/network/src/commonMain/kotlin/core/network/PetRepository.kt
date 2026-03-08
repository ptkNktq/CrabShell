package core.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import model.Pet

interface PetRepository {
    suspend fun getPets(): List<Pet>

    suspend fun updatePetName(
        petId: String,
        name: String,
    ): Pet
}

class PetRepositoryImpl(
    private val client: HttpClient,
) : PetRepository {
    override suspend fun getPets(): List<Pet> = client.get("/api/pets").body()

    override suspend fun updatePetName(
        petId: String,
        name: String,
    ): Pet =
        client
            .put("/api/pets/$petId") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("name" to name))
            }.body()
}
