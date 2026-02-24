package server.quest

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestExpiryTest {
    private val today = LocalDate.of(2024, 7, 15)

    @Test
    fun openQuestPastDeadlineIsExpired() {
        assertTrue(isQuestExpired("Open", "2024-07-14", today))
    }

    @Test
    fun acceptedQuestPastDeadlineIsExpired() {
        assertTrue(isQuestExpired("Accepted", "2024-07-14", today))
    }

    @Test
    fun openQuestOnDeadlineDayIsNotExpired() {
        assertFalse(isQuestExpired("Open", "2024-07-15", today))
    }

    @Test
    fun openQuestBeforeDeadlineIsNotExpired() {
        assertFalse(isQuestExpired("Open", "2024-07-16", today))
    }

    @Test
    fun verifiedQuestPastDeadlineIsNotExpired() {
        assertFalse(isQuestExpired("Verified", "2024-07-14", today))
    }

    @Test
    fun expiredQuestIsNotReExpired() {
        assertFalse(isQuestExpired("Expired", "2024-07-14", today))
    }

    @Test
    fun nullDeadlineIsNotExpired() {
        assertFalse(isQuestExpired("Open", null, today))
    }

    @Test
    fun deadlineWithTimeComponentIsHandled() {
        assertTrue(isQuestExpired("Open", "2024-07-14 23:59", today))
    }
}
