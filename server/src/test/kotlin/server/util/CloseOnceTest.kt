package server.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class CloseOnceTest {
    @Test
    fun runsActionOnlyOnFirstCall() {
        val closeOnce = CloseOnce()
        var count = 0

        repeat(3) { closeOnce { count++ } }

        assertEquals(1, count)
    }

    @Test
    fun runsActionOnlyOnceWhenCalledConcurrently() {
        val closeOnce = CloseOnce()
        val count = AtomicInteger(0)
        val threads = 8
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threads)
        try {
            repeat(threads) {
                executor.submit {
                    start.await()
                    closeOnce { count.incrementAndGet() }
                }
            }
            start.countDown()
        } finally {
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        assertEquals(1, count.get())
    }
}
