package server.feeding

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.util.getOrFail
import model.MealTime
import org.koin.ktor.ext.inject

fun Route.quickFeedRoutes() {
    val quickFeedService by inject<QuickFeedService>()

    get("/feeding/quick-done") {
        val petId = call.parameters.getOrFail("pet")
        val date = call.parameters.getOrFail("date")
        val mealName = call.parameters.getOrFail("meal")
        val token = call.parameters.getOrFail("token")

        val mealTime =
            MealTime.entries.find { it.name == mealName }
                ?: return@get call.respondText(
                    errorHtml("不正なパラメータです"),
                    ContentType.Text.Html,
                    HttpStatusCode.BadRequest,
                )

        val success = quickFeedService.execute(petId, date, mealTime, token)
        if (success) {
            val mealLabel =
                when (mealTime) {
                    MealTime.MORNING -> "朝"
                    MealTime.LUNCH -> "昼"
                    MealTime.EVENING -> "晩"
                }
            call.respondText(
                successHtml("${mealLabel}ごはん完了", date),
                ContentType.Text.Html,
            )
        } else {
            call.respondText(
                errorHtml("トークンが無効です"),
                ContentType.Text.Html,
                HttpStatusCode.Forbidden,
            )
        }
    }
}

private fun successHtml(
    title: String,
    date: String,
): String =
    """
    <!DOCTYPE html>
    <html lang="ja">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <style>
            body { font-family: sans-serif; display: flex; justify-content: center; align-items: center;
                   min-height: 100vh; margin: 0; background: #1a1a2e; color: #e0e0e0; }
            .card { text-align: center; background: #16213e; border-radius: 16px; padding: 48px 32px;
                    box-shadow: 0 4px 24px rgba(0,0,0,0.3); }
            .check { font-size: 64px; margin-bottom: 16px; }
            h1 { color: #4ecca3; margin: 0 0 8px; }
            p { color: #a0a0a0; margin: 0; }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="check">&#x2705;</div>
            <h1>$title</h1>
            <p>$date</p>
        </div>
    </body>
    </html>
    """.trimIndent()

private fun errorHtml(message: String): String =
    """
    <!DOCTYPE html>
    <html lang="ja">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>エラー</title>
        <style>
            body { font-family: sans-serif; display: flex; justify-content: center; align-items: center;
                   min-height: 100vh; margin: 0; background: #1a1a2e; color: #e0e0e0; }
            .card { text-align: center; background: #16213e; border-radius: 16px; padding: 48px 32px;
                    box-shadow: 0 4px 24px rgba(0,0,0,0.3); }
            .icon { font-size: 64px; margin-bottom: 16px; }
            h1 { color: #e74c3c; margin: 0; }
        </style>
    </head>
    <body>
        <div class="card">
            <div class="icon">&#x274C;</div>
            <h1>$message</h1>
        </div>
    </body>
    </html>
    """.trimIndent()
