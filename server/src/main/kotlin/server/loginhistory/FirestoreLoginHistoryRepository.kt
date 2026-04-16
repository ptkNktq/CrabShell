package server.loginhistory

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import model.LoginEvent
import server.util.await

class FirestoreLoginHistoryRepository(
    private val firestore: Firestore,
) : LoginHistoryRepository {
    private fun userCollection(uid: String) = firestore.collection("users").document(uid).collection("loginHistory")

    override suspend fun recordLogin(
        uid: String,
        event: LoginEvent,
    ) {
        val data =
            buildMap<String, Any?> {
                put("timestamp", event.timestamp)
                put("ipAddress", event.ipAddress)
                put("userAgent", event.userAgent)
                put("loginMethod", event.loginMethod)
                put("expireAt", event.expireAt)
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
                timestamp = doc.getString("timestamp") ?: "",
                ipAddress = doc.getString("ipAddress"),
                userAgent = doc.getString("userAgent"),
                loginMethod = doc.getString("loginMethod"),
                country = doc.getString("country"),
                region = doc.getString("region"),
                city = doc.getString("city"),
                suspicious = doc.getBoolean("suspicious"),
                deviceFingerprint = doc.getString("deviceFingerprint"),
                expireAt = doc.getString("expireAt"),
            )
        }
    }
}
