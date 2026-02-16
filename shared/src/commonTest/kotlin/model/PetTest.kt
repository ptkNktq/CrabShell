package model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PetTest {
    private val json = Json

    @Test
    fun petRoundTrip() {
        val pet = Pet(id = "p1", name = "Tama")
        val encoded = json.encodeToString(Pet.serializer(), pet)
        val decoded = json.decodeFromString(Pet.serializer(), encoded)
        assertEquals(pet, decoded)
    }
}
