package server.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * [AutoCloseable.close] を冪等にするための部品。初回だけ後始末を実行し、2 回目以降は何もしない。
 *
 * 「1 回だけ実行する」判定をここに集約し、各クラスは何を閉じるかだけを書く。
 * 後始末が例外を投げても閉じたものとして扱い、再試行はしない。
 * ```
 * private val closeOnce = CloseOnce()
 * override fun close() = closeOnce { resource.close() }
 * ```
 */
class CloseOnce {
    private val closed = AtomicBoolean(false)

    /** 初回の呼び出しでだけ [action] を実行する。複数スレッドから同時に呼ばれても実行は 1 回 */
    operator fun invoke(action: () -> Unit) {
        if (closed.compareAndSet(false, true)) {
            action()
        }
    }
}
