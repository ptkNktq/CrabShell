package server.di

import org.koin.test.verify.verify
import server.cache.Cacheable
import kotlin.test.Test

/**
 * serverModule の定義のコンストラクタ引数が、すべて DI で解決できるかを静的に検証する。
 * インスタンスは生成しないため、Firebase や DB への接続は発生しない。
 *
 * 検証できないもの:
 * - ルートハンドラの `by inject<T>()` で取り出す型（定義のコンストラクタ引数ではないため）
 * - 修飾子（`named(...)`）の一致（Koin の verify はクラス名でしか照合しない）
 */
class ServerModuleTest {
    @Test
    fun serverModuleDefinitionsAreResolvable() {
        serverModule.verify(
            extraTypes =
                listOf(
                    // PasskeyConfig.allowedOrigins。DI 対象ではなく環境変数から作る値
                    Set::class,
                    // CacheManager(List<Cacheable>)。Repository を Cacheable にキャストして渡しており、Cacheable 自体は定義しない
                    Cacheable::class,
                ),
        )
    }
}
