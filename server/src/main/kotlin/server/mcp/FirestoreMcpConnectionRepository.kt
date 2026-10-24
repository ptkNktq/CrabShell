package server.mcp

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.Query
import org.slf4j.LoggerFactory
import server.util.await
import java.time.Instant
import com.google.cloud.Timestamp as FirestoreTimestamp

/** `users/{uid}/mcpConnections/{connectionId}` に連携アプリを保存する */
class FirestoreMcpConnectionRepository(
    private val firestore: Firestore,
) : McpConnectionRepository {
    private val logger = LoggerFactory.getLogger(FirestoreMcpConnectionRepository::class.java)

    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("mcpConnections")

    override suspend fun find(
        uid: String,
        connectionId: String,
    ): McpConnectionRecord? =
        collection(uid)
            .document(connectionId)
            .get()
            .await()
            .toRecord()

    override suspend fun list(
        uid: String,
        limit: Int,
    ): List<McpConnectionRecord> =
        collection(uid)
            .orderBy(FIELD_LAST_USED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRecord() }

    // orderBy はそのフィールドを持たないドキュメントを結果から除く（有効に戻すと無効にした日時は消す）ため、
    // 無効にした日時で並べるだけで無効のものに絞れる（https://firebase.google.com/docs/firestore/query-data/order-limit-data）
    override suspend fun listDisabled(uid: String): List<McpConnectionRecord> =
        collection(uid)
            .orderBy(FIELD_DISABLED_AT, Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRecord() }

    override suspend fun createIfAbsent(
        uid: String,
        record: McpConnectionRecord,
    ) {
        val data =
            buildMap {
                put(FIELD_CLIENT_ID, record.clientId)
                put(FIELD_FIRST_USED_AT, record.firstUsedAt.toFirestoreTimestamp())
                put(FIELD_LAST_USED_AT, record.lastUsedAt.toFirestoreTimestamp())
                record.clientName?.let { put(FIELD_CLIENT_NAME, it) }
                record.clientNameFetchedAt?.let { put(FIELD_CLIENT_NAME_FETCHED_AT, it.toFirestoreTimestamp()) }
                record.disabledAt?.let { put(FIELD_DISABLED_AT, it.toFirestoreTimestamp()) }
            }
        val ref = collection(uid).document(record.id)
        // 同時に来た別のリクエストが先に作っていれば何もしない（無効にした日時などを上書きしないため）
        firestore
            .runTransaction { transaction ->
                if (!transaction.get(ref).get().exists()) transaction.create(ref, data)
            }.await()
    }

    override suspend fun updateLastUsedAt(
        uid: String,
        connectionId: String,
        lastUsedAt: Instant,
    ) {
        collection(uid).document(connectionId).update(FIELD_LAST_USED_AT, lastUsedAt.toFirestoreTimestamp()).await()
    }

    override suspend fun updateClientName(
        uid: String,
        connectionId: String,
        clientName: String?,
        fetchedAt: Instant,
    ) {
        collection(uid)
            .document(connectionId)
            .update(
                mapOf(
                    FIELD_CLIENT_NAME to (clientName ?: FieldValue.delete()),
                    FIELD_CLIENT_NAME_FETCHED_AT to fetchedAt.toFirestoreTimestamp(),
                ),
            ).await()
    }

    override suspend fun updateDisabledAt(
        uid: String,
        connectionId: String,
        disabledAt: Instant?,
    ): McpConnectionRecord? {
        val ref = collection(uid).document(connectionId)
        // 存在しない記録を作ってしまわないよう、読んでから更新する
        return firestore
            .runTransaction { transaction ->
                val snapshot = transaction.get(ref).get()
                val record = snapshot.toRecord() ?: return@runTransaction null
                transaction.update(ref, FIELD_DISABLED_AT, disabledAt?.toFirestoreTimestamp() ?: FieldValue.delete())
                record.copy(disabledAt = disabledAt)
            }.await()
    }

    private fun DocumentSnapshot.toRecord(): McpConnectionRecord? {
        if (!exists()) return null
        val clientId = getString(FIELD_CLIENT_ID)
        val firstUsedAt = instant(FIELD_FIRST_USED_AT)
        val lastUsedAt = instant(FIELD_LAST_USED_AT)
        if (clientId == null || firstUsedAt == null || lastUsedAt == null) {
            logger.warn("MCP connection doc {} skipped: missing required fields", id)
            return null
        }
        return McpConnectionRecord(
            id = id,
            clientId = clientId,
            clientName = getString(FIELD_CLIENT_NAME),
            clientNameFetchedAt = instant(FIELD_CLIENT_NAME_FETCHED_AT),
            firstUsedAt = firstUsedAt,
            lastUsedAt = lastUsedAt,
            disabledAt = instant(FIELD_DISABLED_AT),
        )
    }

    private fun DocumentSnapshot.instant(field: String): Instant? = (get(field) as? FirestoreTimestamp)?.toDate()?.toInstant()

    private fun Instant.toFirestoreTimestamp() = FirestoreTimestamp.ofTimeSecondsAndNanos(epochSecond, nano)

    companion object {
        private const val FIELD_CLIENT_ID = "clientId"
        private const val FIELD_CLIENT_NAME = "clientName"
        private const val FIELD_CLIENT_NAME_FETCHED_AT = "clientNameFetchedAt"
        private const val FIELD_FIRST_USED_AT = "firstUsedAt"
        private const val FIELD_LAST_USED_AT = "lastUsedAt"
        private const val FIELD_DISABLED_AT = "disabledAt"
    }
}
