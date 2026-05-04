package server.loginhistory

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import model.LoginEvent
import model.LoginMethod
import org.slf4j.LoggerFactory
import server.util.await
import java.time.Instant
import com.google.cloud.Timestamp as FirestoreTimestamp

class FirestoreLoginHistoryRepository(
    private val firestore: Firestore,
) : LoginHistoryRepository {
    private val logger = LoggerFactory.getLogger(FirestoreLoginHistoryRepository::class.java)

    private fun userCollection(uid: String) = firestore.collection("users").document(uid).collection("loginHistory")

    private fun Instant.toFirestoreTimestamp() = FirestoreTimestamp.ofTimeSecondsAndNanos(epochSecond, nano)

    override suspend fun recordLogin(
        uid: String,
        input: RecordLoginInput,
    ) {
        val data =
            buildMap {
                put("timestamp", input.timestamp.toFirestoreTimestamp())
                input.ipAddress?.let { put("ipAddress", it) }
                input.userAgent?.let { put("userAgent", it) }
                input.loginMethod?.let { put("loginMethod", it.name) }
                input.country?.let { put("country", it) }
                input.region?.let { put("region", it) }
                input.city?.let { put("city", it) }
                put("expireAt", input.expireAt.toFirestoreTimestamp())
            }
        userCollection(uid).document(input.docId).set(data).await()
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

        return docs.mapNotNull { doc ->
            val timestamp =
                (doc.get("timestamp") as? FirestoreTimestamp)
                    ?.toDate()
                    ?.toInstant()
                    ?.toString()
            if (timestamp == null) {
                logger.warn("Login history doc ${doc.id} skipped: missing or invalid timestamp")
                return@mapNotNull null
            }
            LoginEvent(
                id = doc.id,
                timestamp = timestamp,
                ipAddress = doc.getString("ipAddress"),
                userAgent = doc.getString("userAgent"),
                loginMethod =
                    doc.getString("loginMethod")?.let { raw ->
                        runCatching { LoginMethod.valueOf(raw) }
                            .onFailure { logger.warn("Login history doc ${doc.id} has unknown loginMethod: $raw") }
                            .getOrNull()
                    },
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
