package server.passkey

import org.slf4j.LoggerFactory
import server.config.EnvConfig

/** WebAuthn で許可するオリジンの集合 */
@JvmInline
value class AllowedOrigins(
    private val origins: Set<String>,
) {
    operator fun contains(origin: String): Boolean = origin in origins

    fun isEmpty(): Boolean = origins.isEmpty()

    override fun toString(): String = origins.toString()
}

/** パスキー（WebAuthn）の Relying Party 設定 */
data class PasskeyConfig(
    val rpId: String,
    val allowedOrigins: AllowedOrigins,
) {
    /** RP ID と許可オリジンの両方が設定されている場合のみパスキー機能を有効にする */
    val enabled: Boolean get() = rpId.isNotEmpty() && !allowedOrigins.isEmpty()

    companion object {
        private val logger = LoggerFactory.getLogger(PasskeyConfig::class.java)

        /** 環境変数 `WEBAUTHN_RP_ID` / `WEBAUTHN_ORIGIN`（カンマ区切り）から生成する */
        fun fromEnv(): PasskeyConfig {
            val config =
                PasskeyConfig(
                    rpId = EnvConfig["WEBAUTHN_RP_ID"]?.trim().orEmpty(),
                    allowedOrigins =
                        AllowedOrigins(
                            EnvConfig["WEBAUTHN_ORIGIN"]
                                .orEmpty()
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet(),
                        ),
                )
            if (!config.enabled) {
                logger.warn("WEBAUTHN_RP_ID / WEBAUTHN_ORIGIN が未設定のためパスキー機能は無効です")
            }
            return config
        }
    }
}
