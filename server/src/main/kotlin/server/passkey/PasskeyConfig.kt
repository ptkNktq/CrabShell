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
            val config = parse(rpId = EnvConfig["WEBAUTHN_RP_ID"], origins = EnvConfig["WEBAUTHN_ORIGIN"])
            if (!config.enabled) {
                logger.warn("WEBAUTHN_RP_ID / WEBAUTHN_ORIGIN が未設定のためパスキー機能は無効です")
            }
            return config
        }

        /**
         * 設定値の文字列から生成する。前後の空白は取り除き、空文字・空白だけの値は未設定として扱う。
         * @param origins カンマ区切りの許可オリジン
         */
        fun parse(
            rpId: String?,
            origins: String?,
        ): PasskeyConfig =
            PasskeyConfig(
                rpId = rpId?.trim().orEmpty(),
                allowedOrigins =
                    AllowedOrigins(
                        origins
                            .orEmpty()
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet(),
                    ),
            )
    }
}
