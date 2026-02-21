package server.config

import io.github.cdimascio.dotenv.Dotenv

object EnvConfig {
    private val dotenv: Dotenv =
        Dotenv
            .configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load()

    operator fun get(key: String): String? = dotenv[key]
}
