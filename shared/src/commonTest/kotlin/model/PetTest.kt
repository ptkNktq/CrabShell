package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PetTest {
    @Test
    fun petRoundTrip() {
        val pet = Pet(id = "p1", name = "Tama")
        val encoded = Json.encodeToString(Pet.serializer(), pet)
        val decoded = Json.decodeFromString(Pet.serializer(), encoded)
        assertEquals(pet, decoded)
    }
}
