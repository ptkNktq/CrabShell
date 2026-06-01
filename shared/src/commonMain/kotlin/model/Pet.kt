package model

import kotlinx.serialization.Serializable

@Serializable
data class Pet(
    val id: String,
    val name: String,
)

/** `PUT /pets/{petId}` のリクエスト DTO。 */
@Serializable
data class PetNameUpdateRequest(
    val name: String,
)
