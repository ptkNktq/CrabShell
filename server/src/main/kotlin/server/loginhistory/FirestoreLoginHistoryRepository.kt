package server.loginhistory

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import model.LoginEvent
import server.util.await
import java.time.Instant
import com.google.cloud.Timestamp as FirestoreTimestamp

class FirestoreLoginHistoryRepository(
    private val firestore: Firestore,
) : LoginHistoryRepository {
    private fun userCollection(uid: String) = firestore.collection("users").document(uid).collection("loginHistory")

    private fun Instant.toFirestoreTimestamp() = FirestoreTimestamp.ofTimeSecondsAndNanos(epochSecond, nano)

    override suspend fun recordLogin(
        uid: String,
        event: LoginEvent,
        timestamp: Instant,
        expireAt: Instant,
    ) {
        val data =
            buildMap<String, Any?> {
                put("timestamp", timestamp.toFirestoreTimestamp())
                put("ipAddress", event.ipAddress)
                put("userAgent", event.userAgent)
                put("loginMethod", event.loginMethod)
                put("expireAt", expireAt.toFirestoreTimestamp())
            }
        userCollection(uid).document(event.id).set(data).await()
    }

    override suspend fun getHistory(
        uid: String,
        limit: Int,
    ): List<LoginEvent> {
        val docs =
            userCollection(uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .documents

        return docs.map { doc ->
            LoginEvent(
                id = doc.id,
                timestamp =
                    (doc.get("timestamp") as? FirestoreTimestamp)
                        ?.toDate()
                        ?.toInstant()
                        ?.toString() ?: "",
                ipAddress = doc.getString("ipAddress"),
                userAgent = doc.getString("userAgent"),
                loginMethod = doc.getString("loginMethod"),
                country = doc.getString("country"),
                region = doc.getString("region"),
                city = doc.getString("city"),
                suspicious = doc.getBoolean("suspicious"),
                deviceFingerprint = doc.getString("deviceFingerprint"),
                // expireAt は Firestore TTL 用の内部フィールドのためクライアントには返さない
            )
        }
    }
}
