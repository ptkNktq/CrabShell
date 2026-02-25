package server.cache

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import model.CacheRefreshResult
import org.koin.ktor.ext.inject
import server.auth.adminOnly

fun Route.cacheRoutes() {
    val cacheManager by inject<CacheManager>()

    route("/admin/cache/clear") {
        adminOnly {
            post({
                tags = listOf("admin")
                summary = "サーバーキャッシュ全クリア（admin）"
                response {
                    code(HttpStatusCode.OK) {
                        body<CacheRefreshResult>()
                    }
                }
            }) {
                val cleared = cacheManager.clearAll()
                call.respond(
                    CacheRefreshResult(
                        clearedCaches = cleared,
                        message = "${cleared.size} 件のキャッシュをクリアしました",
                    ),
                )
            }
        }
    }
}
