package server.di

import org.koin.core.definition.KoinDefinition
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("server.di.CloseOnStop")

/**
 * Koin の close 時（Ktor の ApplicationStopping で koin-ktor が呼ぶ）に、この定義のインスタンスを [AutoCloseable.close] する。
 *
 * - Koin は Closeable を自動では閉じないため、閉じたいリソースの定義にはこれを付ける
 * - Koin は各定義の onClose を順に呼ぶだけで例外を捕まえないため、1 つの失敗で残りが閉じられなくならないよう
 *   例外はログに出して握りつぶす
 * - onClose の呼ばれる順序は依存関係と無関係で保証されないため、他の DI 管理リソースに依存する後始末は書かない
 * - 作成されていない定義では何もしない
 */
fun <T : AutoCloseable> KoinDefinition<T>.closeOnStop(): KoinDefinition<T> =
    withOptions {
        onClose { resource -> resource?.let(::closeQuietly) }
    }

/** [resource] を閉じ、失敗した場合は WARN ログを出して例外を外に出さない */
internal fun closeQuietly(resource: AutoCloseable) {
    val name = resource::class.simpleName
    try {
        resource.close()
        logger.info("Closed {}", name)
    } catch (e: Exception) {
        logger.warn("Failed to close {}", name, e)
    }
}
