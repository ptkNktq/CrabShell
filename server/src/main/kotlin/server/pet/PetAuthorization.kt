package server.pet

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.server.util.getOrFail
import server.auth.firebasePrincipal

/**
 * petId パスパラメータに対してメンバー認可を検証する。
 * メンバーでない場合は 403 を返して例外をスローし、呼び出し元のハンドラを中断する。
 */
suspend fun RoutingCall.verifyPetMember(petRepository: PetRepository) {
    val petId = parameters.getOrFail("petId")
    val uid = firebasePrincipal.uid
    if (!petRepository.isMember(petId, uid)) {
        respond(HttpStatusCode.Forbidden, mapOf("error" to "Not a member of this pet"))
        throw PetAccessDeniedException(petId, uid)
    }
}

class PetAccessDeniedException(
    petId: String,
    uid: String,
) : RuntimeException("User $uid is not a member of pet $petId")
