package server.mcp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import model.McpConnection
import org.slf4j.LoggerFactory
import server.util.sanitizeForLog
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** [McpConnectionService.checkAndRecordUse] の結果 */
enum class McpConnectionUse {
    /** 利用してよい（利用の記録は間引きや書き込みの失敗で行わない場合がある） */
    ALLOWED,

    /** ユーザーが無効にしたアプリ */
    DISABLED,
}

/**
 * 連携アプリ（ユーザー × MCP クライアント）の記録・一覧・無効化。
 *
 * WorkOS は許可をユーザー × クライアント単位でしか持たず、トークンの更新と連携し直しをクレームで見分けられない
 * （どちらも同じ `sid` のまま新しい `iat` で届く）うえ、Connect の許可を失効させる API もない。
 * そのため「無効」は WorkOS 側の連携を残したまま CrabShell が通さない状態として持ち、有効に戻せば手元のトークンがまた通る。
 */
class McpConnectionService(
    private val repository: McpConnectionRepository,
    private val clientMetadataFetcher: ClientMetadataFetcher,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(McpConnectionService::class.java)

    /**
     * `/mcp` の認証のたびに呼び、無効にされたアプリでないかを確かめて利用を記録する。
     *
     * 最終利用日時の書き込みは [LAST_USED_UPDATE_INTERVAL] ごとに間引く（リクエストごとに書き込まない）。
     * 記録の書き込みに失敗しても利用は止めない（判定に必要な読み取りの失敗だけを例外で伝える）。
     *
     * @throws Exception 記録の読み取りに失敗した場合（呼び出し側で一時的な障害として扱う）
     */
    suspend fun checkAndRecordUse(
        uid: String,
        clientId: String,
    ): McpConnectionUse {
        val connectionId = mcpConnectionId(clientId)
        val now = clock.instant()
        val record = repository.find(uid, connectionId)
        if (record?.disabledAt != null) return McpConnectionUse.DISABLED

        try {
            if (record == null) {
                repository.createIfAbsent(
                    uid,
                    McpConnectionRecord(
                        id = connectionId,
                        clientId = clientId,
                        clientName = null,
                        clientNameFetchedAt = null,
                        firstUsedAt = now,
                        lastUsedAt = now,
                        disabledAt = null,
                    ),
                )
                logger.info("MCP connection recorded: uid={} client_id={}", uid, sanitizeForLog(clientId))
            } else if (Duration.between(record.lastUsedAt, now) >= LAST_USED_UPDATE_INTERVAL) {
                repository.updateLastUsedAt(uid, connectionId, now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to record MCP connection use: uid={} client_id={}", uid, sanitizeForLog(clientId), e)
        }
        return McpConnectionUse.ALLOWED
    }

    /**
     * ユーザーの連携アプリの一覧（最終利用日時の新しい順）。
     * 表示名が未取得、または取り直す間隔（[CLIENT_NAME_REFRESH_INTERVAL]。名前が取れていなければ [CLIENT_NAME_RETRY_INTERVAL]）が
     * 過ぎたものは、ここでメタデータを取り直して保存する（`/mcp` の応答を遅らせないよう、認証の中では取りに行かない）。
     *
     * 応答の遅いホストがあっても一覧を返せるよう、取り直しは [METADATA_FETCH_DEADLINE_MILLIS] で打ち切る。
     * 締め切りまでに終わらなかったもの（同時取得数の枠を待ち、遅れて始めたものを含む）は今の表示名のまま返し、
     * 保存もしない（次に一覧を開いたときに取り直す）。
     * 1 件の取得のタイムアウト（[CimdClientMetadataFetcher.REQUEST_TIMEOUT_MILLIS]）は保存の時間を見込んでこれより短くしてあり、
     * すぐに取り始めた応答しないホストは、保存がその余裕の時間内に終われば締め切りの前に失敗として保存されるため、
     * 毎回待たされ続けることはない（保存が遅れて締め切りに間に合わなかった場合は、次に一覧を開いたときにまた取りに行く）。
     */
    suspend fun list(uid: String): List<McpConnection> {
        val now = clock.instant()
        val records = repository.list(uid)
        val semaphore = Semaphore(MAX_CONCURRENT_METADATA_FETCHES)
        return coroutineScope {
            records
                .map { record ->
                    async {
                        if (!needsClientNameRefresh(record, now)) return@async record
                        // 締め切りは全件で同時に数え始めるため、待ち行列で待った時間も含めて一覧全体の上限になる
                        withTimeoutOrNull(METADATA_FETCH_DEADLINE_MILLIS) {
                            semaphore.withPermit { refreshClientName(uid, record, now) }
                        } ?: record
                    }
                }.awaitAll()
        }.map { it.toModel() }
    }

    /**
     * 無効にした連携アプリ（無効にした日の新しい順）。MCP 連携画面で、連携し直すアプリを有効に戻してもらうために使う。
     * 画面の表示を待たせないよう、表示名は保存済みのもの（なければホスト名）を使い、メタデータは取り直さない
     */
    suspend fun listDisabled(uid: String): List<McpConnection> = repository.listDisabled(uid).map { it.toModel() }

    /**
     * 連携アプリを無効にする・有効に戻す。
     * @return 更新後の連携アプリ。対象がなければ null
     */
    suspend fun setDisabled(
        uid: String,
        connectionId: String,
        disabled: Boolean,
    ): McpConnection? {
        val record = repository.updateDisabledAt(uid, connectionId, if (disabled) clock.instant() else null) ?: return null
        logger.info("MCP connection {}: uid={} client_id={}", if (disabled) "disabled" else "enabled", uid, sanitizeForLog(record.clientId))
        return record.toModel()
    }

    private fun needsClientNameRefresh(
        record: McpConnectionRecord,
        now: Instant,
    ): Boolean {
        val fetchedAt = record.clientNameFetchedAt ?: return true
        // 名前が取れていない（前回の取得に失敗した）ものは、一時的な障害から早く戻れるよう短い間隔で取り直す
        val interval = if (record.clientName == null) CLIENT_NAME_RETRY_INTERVAL else CLIENT_NAME_REFRESH_INTERVAL
        return Duration.between(fetchedAt, now) >= interval
    }

    /** 取り直せなかった場合は前回の表示名を残す（一時的な障害で名前が消えないように） */
    private suspend fun refreshClientName(
        uid: String,
        record: McpConnectionRecord,
        now: Instant,
    ): McpConnectionRecord {
        val clientName = clientMetadataFetcher.fetchClientName(record.clientId) ?: record.clientName
        try {
            repository.updateClientName(uid, record.id, clientName, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to save MCP client name: uid={} client_id={}", uid, sanitizeForLog(record.clientId), e)
        }
        return record.copy(clientName = clientName, clientNameFetchedAt = now)
    }

    private fun McpConnectionRecord.toModel() =
        McpConnection(
            id = id,
            clientName = clientName ?: fallbackClientName(clientId),
            firstUsedAt = firstUsedAt.toString(),
            lastUsedAt = lastUsedAt.toString(),
            disabledAt = disabledAt?.toString(),
        )

    companion object {
        // 最終利用日時を書き込む間隔（これより短い間隔の利用では書き込まない）
        internal val LAST_USED_UPDATE_INTERVAL: Duration = Duration.ofMinutes(5)

        // 表示名を取り直す間隔
        internal val CLIENT_NAME_REFRESH_INTERVAL: Duration = Duration.ofDays(7)

        // 表示名が取れていないものを取り直す間隔
        internal val CLIENT_NAME_RETRY_INTERVAL: Duration = Duration.ofHours(1)

        // 取得に失敗した後、締め切りまでに表示名（の取得日時）を保存するために残しておく時間
        internal const val METADATA_SAVE_MARGIN_MILLIS = 2_000L

        // 一覧の表示時にメタデータの取り直しを待つ上限（5 秒）。フロントの API のタイムアウト（10 秒）に収める。
        // 1 件の取得のタイムアウトに保存の時間を足して作り、応答しないホストでも締め切りの前に失敗を保存できるようにする
        internal const val METADATA_FETCH_DEADLINE_MILLIS = CimdClientMetadataFetcher.REQUEST_TIMEOUT_MILLIS + METADATA_SAVE_MARGIN_MILLIS

        // 一覧の表示時にメタデータを同時に取りに行く数の上限
        private const val MAX_CONCURRENT_METADATA_FETCHES = 4

        /** 表示名が取れない場合は client_id のホスト名を出す（URL でなければそのまま） */
        internal fun fallbackClientName(clientId: String): String =
            runCatching { URI(clientId).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: clientId
    }
}

/** client_id から連携アプリの ID を作る。client_id は URL で Firestore のドキュメント ID に使えないため、SHA-256 の 16 進にする */
fun mcpConnectionId(clientId: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(clientId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** [mcpConnectionId] の形式（SHA-256 の 16 進）か */
fun isMcpConnectionId(value: String): Boolean = MCP_CONNECTION_ID_PATTERN.matches(value)

private val MCP_CONNECTION_ID_PATTERN = Regex("^[0-9a-f]{64}$")
