package server.mcp

import org.slf4j.LoggerFactory
import server.config.EnvConfig
import java.net.URI

/** MCP エンドポイントのパス */
const val MCP_PATH = "/mcp"

/** MCP エンドポイントの Protected Resource Metadata（RFC 9728）のパス */
const val MCP_RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource$MCP_PATH"

/**
 * MCP サーバーの設定。認可サーバーには WorkOS AuthKit（Standalone Connect）を使う。
 *
 * @property apiKey WorkOS の API キー（完了 API の認証に使う）
 * @property issuer AuthKit のドメイン（`https://xxx.authkit.app`）。アクセストークンの `iss`
 * @property appUrl アプリの公開 URL（末尾の `/` なし）。MCP のリソース URL の基点
 */
class McpConfig(
    val apiKey: String,
    val issuer: String,
    val appUrl: String,
) {
    /** API キー・AuthKit ドメイン・アプリの公開 URL がすべて設定されている場合のみ MCP を有効にする */
    val enabled: Boolean get() = apiKey.isNotEmpty() && issuer.isNotEmpty() && appUrl.isNotEmpty()

    /** この MCP サーバーのリソース URL。アクセストークンの `aud` で、WorkOS の Resource Indicator と一致させる */
    val resource: String get() = "$appUrl$MCP_PATH"

    /** Protected Resource Metadata の URL。401 応答の `WWW-Authenticate` で MCP クライアントに知らせる */
    val resourceMetadataUrl: String get() = "$appUrl$MCP_RESOURCE_METADATA_PATH"

    /** アクセストークンの署名検証に使う公開鍵の URL */
    val jwksUrl: String get() = "$issuer/oauth2/jwks"

    // API キーをログに出さない
    override fun toString(): String = "McpConfig(issuer=$issuer, appUrl=$appUrl, enabled=$enabled)"

    companion object {
        private val logger = LoggerFactory.getLogger(McpConfig::class.java)

        /** 環境変数 `WORKOS_API_KEY` / `WORKOS_AUTHKIT_DOMAIN` / `APP_URL` から生成する */
        fun fromEnv(): McpConfig {
            val config =
                parse(
                    apiKey = EnvConfig["WORKOS_API_KEY"],
                    authKitDomain = EnvConfig["WORKOS_AUTHKIT_DOMAIN"],
                    appUrl = EnvConfig["APP_URL"],
                )
            if (!config.enabled) {
                logger.warn("MCP disabled: WORKOS_API_KEY / WORKOS_AUTHKIT_DOMAIN / APP_URL is missing or invalid")
            }
            return config
        }

        /**
         * 設定値の文字列から生成する。前後の空白は取り除き、空文字・空白だけの値は未設定として扱う。
         * @param authKitDomain スキームを省略した場合は `https://` を補う
         * @param appUrl `http(s)://ホスト` の形でない値は未設定として扱う
         */
        fun parse(
            apiKey: String?,
            authKitDomain: String?,
            appUrl: String?,
        ): McpConfig =
            McpConfig(
                apiKey = apiKey?.trim().orEmpty(),
                issuer = normalizeAuthKitDomain(authKitDomain),
                appUrl = normalizeAppUrl(appUrl),
            )

        private fun normalizeAuthKitDomain(value: String?): String {
            val trimmed = value?.trim()?.trimEnd('/').orEmpty()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) trimmed else "https://$trimmed"
        }

        private fun normalizeAppUrl(value: String?): String {
            val trimmed = value?.trim()?.trimEnd('/').orEmpty()
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return ""
            val validScheme = uri.scheme == "https" || uri.scheme == "http"
            return if (validScheme && !uri.host.isNullOrEmpty()) trimmed else ""
        }
    }
}
