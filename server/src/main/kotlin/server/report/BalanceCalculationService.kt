package server.report

import model.MonthlyMoney

data class UserOverpayment(
    val uid: String,
    val overpaid: Long,
    val redeemed: Long,
) {
    val net: Long get() = (overpaid - redeemed).coerceAtLeast(0L)
}

data class BalanceResult(
    val months: List<String>,
    val overpayments: List<UserOverpayment>,
)

class BalanceCalculationService {
    fun calculateOverpayments(allMonths: List<MonthlyMoney>): BalanceResult {
        val months = mutableListOf<String>()
        val overpaidByUser = mutableMapOf<String, Long>()
        val redeemedByUser = mutableMapOf<String, Long>()

        for (monthData in allMonths) {
            months.add(monthData.month)

            val monthAllocated = mutableMapOf<String, Long>()
            for (item in monthData.items) {
                for (payment in item.payments) {
                    monthAllocated[payment.uid] =
                        (monthAllocated[payment.uid] ?: 0L) + payment.amount
                }
            }

            val monthPaid = mutableMapOf<String, Long>()
            for (record in monthData.paymentRecords) {
                if (record.isRedemption) {
                    redeemedByUser[record.uid] =
                        (redeemedByUser[record.uid] ?: 0L) + record.amount
                } else {
                    monthPaid[record.uid] =
                        (monthPaid[record.uid] ?: 0L) + record.amount
                }
            }

            val uidsInMonth = monthAllocated.keys + monthPaid.keys
            for (uid in uidsInMonth) {
                val diff = (monthPaid[uid] ?: 0L) - (monthAllocated[uid] ?: 0L)
                if (diff > 0L) {
                    overpaidByUser[uid] = (overpaidByUser[uid] ?: 0L) + diff
                }
            }
        }

        months.sort()

        return BalanceResult(
            months = months,
            overpayments =
                overpaidByUser.map { (uid, overpaid) ->
                    UserOverpayment(
                        uid = uid,
                        overpaid = overpaid,
                        redeemed = redeemedByUser[uid] ?: 0L,
                    )
                },
        )
    }
}
