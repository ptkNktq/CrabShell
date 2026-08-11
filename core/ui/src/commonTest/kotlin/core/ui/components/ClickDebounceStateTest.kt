package core.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class ClickDebounceStateTest {
    @Test
    fun tryClick_firstClickIsAccepted() {
        val state = ClickDebounceState(TestTimeSource())
        assertTrue(state.tryClick(300))
    }

    @Test
    fun tryClick_rejectsClickWithinDebounceWindow() {
        val timeSource = TestTimeSource()
        val state = ClickDebounceState(timeSource)
        assertTrue(state.tryClick(300))
        timeSource += 299.milliseconds
        assertFalse(state.tryClick(300))
    }

    @Test
    fun tryClick_acceptsClickAfterDebounceWindow() {
        val timeSource = TestTimeSource()
        val state = ClickDebounceState(timeSource)
        assertTrue(state.tryClick(300))
        timeSource += 300.milliseconds
        assertTrue(state.tryClick(300))
    }

    @Test
    fun tryClick_rejectionDoesNotResetWindow() {
        val timeSource = TestTimeSource()
        val state = ClickDebounceState(timeSource)
        assertTrue(state.tryClick(300))
        timeSource += 200.milliseconds
        assertFalse(state.tryClick(300))
        timeSource += 100.milliseconds
        // 拒否されたクリックが基準時刻を更新しないこと（最初の受理から300ms経過で受理）
        assertTrue(state.tryClick(300))
    }

    @Test
    fun tryClick_zeroDebounceAlwaysAccepts() {
        val state = ClickDebounceState(TestTimeSource())
        assertTrue(state.tryClick(0))
        assertTrue(state.tryClick(0))
    }
}
