package server.auth

import com.google.firebase.auth.FirebaseToken
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private const val AUTH_PROVIDER_NAME = "firebase"

/** Firebase ID Token を検証した結果を保持する Principal */
data class FirebasePrincipal(
    val firebaseToken: FirebaseToken,
) : Principal {
    val uid: String get() = firebaseToken.uid
    val email: String? get() = firebaseToken.email
    val name: String? get() = firebaseToken.name
    val claims: Map<String, Any> get() = firebaseToken.claims
    val isAdmin: Boolean get() = claims["admin"] == true
}

/** Ktor Authentication プラグインを設定（Bearer トークンで Firebase ID Token を検証） */
fun Application.configureAuth() {
    install(Authentication) {
        bearer(AUTH_PROVIDER_NAME) {
            realm = "CrabShell"
            authenticate { credential ->
                FirebaseAdmin.verifyIdToken(credential.token)?.let { FirebasePrincipal(it) }
            }
        }
    }
}

/** authenticated / adminOnly ブロック内で FirebasePrincipal を取得する拡張プロパティ */
val ApplicationCall.firebasePrincipal: FirebasePrincipal
    get() = principal<FirebasePrincipal>()!!

/** 認証必須ルートビルダー */
fun Route.authenticated(build: Route.() -> Unit): Route = authenticate(AUTH_PROVIDER_NAME) { build() }

/** admin 認可プラグイン（AuthenticationChecked フックで admin クレームを検証） */
private val AdminAuthorizationPlugin =
    createRouteScopedPlugin("AdminAuthorization") {
        on(AuthenticationChecked) { call ->
            if (call.principal<FirebasePrincipal>()?.isAdmin != true) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
            }
        }
    }

/** admin 限定ルートビルダー（認証 + admin カスタムクレーム検証） */
fun Route.adminOnly(build: Route.() -> Unit): Route =
    authenticate(AUTH_PROVIDER_NAME) {
        install(AdminAuthorizationPlugin)
        build()
    }
