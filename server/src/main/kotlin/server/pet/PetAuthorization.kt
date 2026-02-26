package server.pet

import io.ktor.server.routing.RoutingCall
import io.ktor.server.util.getOrFail
import server.auth.firebasePrincipal

/**
 * petId パスパラメータに対してメンバー認可を検証し、検証済みの petId を返す。
 * メンバーでない場合は [PetAccessDeniedException] をスローする（StatusPages でハンドリング）。
 */
suspend fun RoutingCall.verifyPetMember(petRepository: PetRepository): String {
    val petId = parameters.getOrFail("petId")
    val uid = firebasePrincipal.uid
    if (!petRepository.isMember(petId, uid)) {
        throw PetAccessDeniedException(petId, uid)
    }
    return petId
}

class PetAccessDeniedException(
    petId: String,
    uid: String,
) : RuntimeException("User $uid is not a member of pet $petId")
