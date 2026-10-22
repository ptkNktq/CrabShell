package server.mcp

import com.google.common.net.InetAddresses
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Dns
import org.slf4j.LoggerFactory
import server.geo.IpClassifier
import server.util.CloseOnce
import server.util.sanitizeForLog
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.UnknownHostException

/** MCP クライアントの表示名を、クライアントの公開するメタデータから取得する */
interface ClientMetadataFetcher : AutoCloseable {
    /**
     * @param clientId MCP クライアントの client_id（CIMD の URL）
     * @return メタデータの `client_name`。取得できない・不正な場合は null
     */
    suspend fun fetchClientName(clientId: String): String?
}

/**
 * Client ID Metadata Document（client_id の URL で公開される JSON）から `client_name` を読む。
 *
 * client_id は利用者側が用意した任意の URL のため、サーバーから内部ネットワークへ接続させられないようにする（SSRF 対策）。
 * - https のみ。リダイレクトは追わない。プロキシも使わない（名前解決がプロキシ側で行われ、[PublicAddressDns] が効かなくなるため）
 * - 名前解決の結果からパブリックでないアドレスを除く（[PublicAddressDns]）。解決したアドレスにそのまま接続するため、
 *   確認した後に別のアドレスへ向け直される（DNS リバインディング）こともない
 * - 応答の大きさに上限を設け（受信しながら数え、上限を超えたら読むのをやめる）、タイムアウトも短くする
 *
 * CIMD の仕様どおり、文書の `client_id` が URL と一致しない場合は採用しない。
 */
class CimdClientMetadataFetcher(
    private val client: HttpClient = createClient(),
) : ClientMetadataFetcher {
    private val logger = LoggerFactory.getLogger(CimdClientMetadataFetcher::class.java)
    private val closeOnce = CloseOnce()

    override suspend fun fetchClientName(clientId: String): String? {
        if (!isFetchableUrl(clientId)) return null
        return try {
            // get() は本文をすべてメモリに読み込んでから返すため、execute の中で読む量を制限する
            // （https://ktor.io/docs/client-responses.html の「Streaming data」）
            client.prepareGet(clientId) { accept(ContentType.Application.Json) }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    logger.info("Client metadata unavailable: status={} client_id={}", response.status.value, sanitizeForLog(clientId))
                    return@execute null
                }
                // 宣言された大きさで超えると分かる場合は読まない
                if ((response.contentLength() ?: 0) > MAX_BODY_BYTES) {
                    logger.info("Client metadata too large: client_id={}", sanitizeForLog(clientId))
                    return@execute null
                }
                // 上限を 1 バイト超えて読み、超えたかどうかで大きすぎる応答を見分ける
                val body = response.bodyAsChannel().readRemaining(MAX_BODY_BYTES + 1).readByteArray()
                if (body.size > MAX_BODY_BYTES) {
                    logger.info("Client metadata too large: client_id={}", sanitizeForLog(clientId))
                    return@execute null
                }
                parseClientName(clientId, body.decodeToString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 例外のメッセージにも URL が含まれうるため、同様に通す
            logger.info("Client metadata fetch failed: client_id={} {}", sanitizeForLog(clientId), sanitizeForLog(e.toString()))
            null
        }
    }

    override fun close() = closeOnce { client.close() }

    companion object {
        // メタデータの応答の上限（CIMD の文書は数 KB 程度）
        internal const val MAX_BODY_BYTES = 64L * 1024

        // 取得のタイムアウト。一覧の応答を待たせないよう、アプリの API の既定値より短くする
        internal const val CONNECT_TIMEOUT_MILLIS = 3_000L
        internal const val REQUEST_TIMEOUT_MILLIS = 5_000L

        // 表示名の上限（文字数）
        internal const val MAX_CLIENT_NAME_LENGTH = 100

        // 表示名から取り除く制御文字（改行・書式制御を含む）
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]")

        private val json = Json { ignoreUnknownKeys = true }

        // OkHttp が IP アドレスとみなして名前解決を経ずに接続するホストの形（okhttp3.internal の canParseAsIpAddress と同じ）。
        // `127.1` のような短縮表記は Guava の判定では IP にならないが、OkHttp はこちらで IP として接続する
        private val OKHTTP_IP_ADDRESS_LIKE = Regex("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)")

        private fun createClient(): HttpClient =
            HttpClient(OkHttp) {
                expectSuccess = false
                followRedirects = false
                install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                }
                engine {
                    config {
                        proxy(Proxy.NO_PROXY)
                        dns(PublicAddressDns())
                        followRedirects(false)
                        followSslRedirects(false)
                    }
                }
            }

        /**
         * https でホスト名があり、資格情報を含まない URL だけを取りに行く。
         * ホストが IP アドレス（OkHttp が IP とみなす形を含む）の場合は名前解決を経ず [PublicAddressDns] の確認をすり抜けるため、取りに行かない
         */
        internal fun isFetchableUrl(value: String): Boolean {
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            val host = uri.host
            return uri.scheme.equals("https", ignoreCase = true) &&
                !host.isNullOrEmpty() &&
                !InetAddresses.isUriInetAddress(host) &&
                !OKHTTP_IP_ADDRESS_LIKE.matches(host) &&
                uri.rawUserInfo == null
        }

        /** 文書の `client_id` が URL と一致する場合だけ `client_name` を返す。表示用に制御文字を除き、長さを切り詰める */
        internal fun parseClientName(
            clientId: String,
            body: String,
        ): String? {
            val document = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            val documentClientId = (document["client_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (documentClientId != clientId) return null
            val name = (document["client_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            return name
                .replace(CONTROL_CHARACTERS, "")
                .trim()
                .let {
                    if (it.codePointCount(0, it.length) >
                        MAX_CLIENT_NAME_LENGTH
                    ) {
                        it.take(it.offsetByCodePoints(0, MAX_CLIENT_NAME_LENGTH))
                    } else {
                        it
                    }
                }.ifEmpty { null }
        }
    }
}

/**
 * 名前解決の結果からパブリックでないアドレス（ループバック・プライベート・リンクローカル等）を除く。
 * すべて除かれた場合は解決できなかったものとして扱い、接続させない。
 */
class PublicAddressDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname).filter { IpClassifier.isPublicAddress(it) }
        if (addresses.isEmpty()) throw UnknownHostException("No public address for $hostname")
        return addresses
    }
}
