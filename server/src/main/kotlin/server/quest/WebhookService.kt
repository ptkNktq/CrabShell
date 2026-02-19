package server.quest

import com.google.firebase.cloud.FirestoreClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import model.Quest
import model.WebhookSettings
import org.slf4j.LoggerFactory
import server.util.await
import java.time.Instant

private val logger = LoggerFactory.getLogger("WebhookService")

object WebhookService {
    private val firestore by lazy { FirestoreClient.getFirestore() }
    private val settingsDoc by lazy { firestore.collection("settings").document("webhook") }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val client =
        HttpClient {
            install(ContentNegotiation) { json() }
        }

    suspend fun getSettings(): WebhookSettings {
        val doc = settingsDoc.get().await()
        if (!doc.exists()) return WebhookSettings()
        val data = doc.data ?: return WebhookSettings()
        @Suppress("UNCHECKED_CAST")
        return WebhookSettings(
            url = data["url"] as? String ?: "",
            enabled = data["enabled"] as? Boolean ?: false,
            events = (data["events"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        )
    }

    suspend fun updateSettings(settings: WebhookSettings) {
        settingsDoc
            .set(
                mapOf(
                    "url" to settings.url,
                    "enabled" to settings.enabled,
                    "events" to settings.events,
                ),
            ).await()
    }

    /** fire-and-forget でイベントを送信 */
    fun notify(
        event: String,
        quest: Quest,
    ) {
        scope.launch {
            try {
                val settings = getSettings()
                if (!settings.enabled || settings.url.isBlank() || event !in settings.events) return@launch

                val payload =
                    WebhookPayload(
                        event = event,
                        quest =
                            WebhookQuestData(
                                title = quest.title,
                                description = quest.description,
                                rewardPoints = quest.rewardPoints,
                                creatorName = quest.creatorName,
                            ),
                        timestamp = Instant.now().toString(),
                    )

                client.post(settings.url) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            } catch (e: Exception) {
                logger.warn("Webhook delivery failed for event=$event: ${e.message}")
            }
        }
    }
}

@Serializable
private data class WebhookPayload(
    val event: String,
    val quest: WebhookQuestData,
    val timestamp: String,
)

@Serializable
private data class WebhookQuestData(
    val title: String,
    val description: String,
    val rewardPoints: Int,
    val creatorName: String,
)
