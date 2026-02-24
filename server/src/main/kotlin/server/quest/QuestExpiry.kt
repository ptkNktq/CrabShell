package server.quest

import java.time.LocalDate

fun isQuestExpired(
    status: String,
    deadline: String?,
    now: LocalDate,
): Boolean =
    deadline != null &&
        (status == "Open" || status == "Accepted") &&
        LocalDate.parse(deadline.take(10)).isBefore(now)
