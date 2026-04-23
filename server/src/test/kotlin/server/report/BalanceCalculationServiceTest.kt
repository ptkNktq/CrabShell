package server.report

import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.PaymentRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceCalculationServiceTest {
    private val service = BalanceCalculationService()

    @Test
    fun emptyDataReturnsEmptyResult() {
        val result = service.calculateOverpayments(emptyList())
        assertEquals(emptyList(), result.yearMonths)
        assertEquals(emptyList(), result.overpayments)
    }

    @Test
    fun singleMonthNoOverpayment() {
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 5000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        assertEquals(listOf("2024-06"), result.yearMonths)
        assertEquals(0, result.overpayments.size)
    }

    @Test
    fun singleMonthWithOverpayment() {
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 3000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        assertEquals(1, result.overpayments.size)
        val overpayment = result.overpayments.first()
        assertEquals("u1", overpayment.uid)
        assertEquals(2000L, overpayment.overpaid)
        assertEquals(0L, overpayment.redeemed)
        assertEquals(2000L, overpayment.net)
    }

    @Test
    fun multipleMonthsMultipleUsers() {
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments =
                                    listOf(
                                        Payment(uid = "u1", amount = 4000L),
                                        Payment(uid = "u2", amount = 6000L),
                                    ),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 7000L, paidAt = "2024-06-01"),
                            PaymentRecord(uid = "u2", amount = 3000L, paidAt = "2024-06-01"),
                        ),
                ),
                MonthlyMoney(
                    yearMonth = "2024-07",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item2",
                                name = "Utilities",
                                amount = 5000L,
                                payments =
                                    listOf(
                                        Payment(uid = "u1", amount = 2500L),
                                        Payment(uid = "u2", amount = 2500L),
                                    ),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 2500L, paidAt = "2024-07-01"),
                            PaymentRecord(uid = "u2", amount = 4000L, paidAt = "2024-07-01"),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        assertEquals(listOf("2024-06", "2024-07"), result.yearMonths)

        val u1 = result.overpayments.find { it.uid == "u1" }!!
        assertEquals(3000L, u1.overpaid) // 6月: 7000 - 4000 = 3000 過払い
        assertEquals(3000L, u1.net)

        val u2 = result.overpayments.find { it.uid == "u2" }!!
        assertEquals(1500L, u2.overpaid) // 7月: 4000 - 2500 = 1500 過払い
        assertEquals(1500L, u2.net)
    }

    @Test
    fun redemptionRecordsReduceNet() {
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 3000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                            PaymentRecord(uid = "u1", amount = 1000L, paidAt = "2024-06-15", isRedemption = true),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        val overpayment = result.overpayments.first()
        assertEquals(2000L, overpayment.overpaid)
        assertEquals(1000L, overpayment.redeemed)
        assertEquals(1000L, overpayment.net)
    }

    @Test
    fun redemptionExceedsOverpaymentClampsToZero() {
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 4000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 5000L, paidAt = "2024-06-01"),
                            PaymentRecord(uid = "u1", amount = 2000L, paidAt = "2024-06-15", isRedemption = true),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        val overpayment = result.overpayments.first()
        assertEquals(1000L, overpayment.overpaid)
        assertEquals(2000L, overpayment.redeemed)
        assertEquals(0L, overpayment.net) // 0 に切り詰め
    }

    @Test
    fun yearMonthsAreSorted() {
        val data =
            listOf(
                MonthlyMoney(yearMonth = "2024-08"),
                MonthlyMoney(yearMonth = "2024-06"),
                MonthlyMoney(yearMonth = "2024-07"),
            )
        val result = service.calculateOverpayments(data)
        assertEquals(listOf("2024-06", "2024-07", "2024-08"), result.yearMonths)
    }

    @Test
    fun underpaymentMonthIsNotCounted() {
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 8000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 3000L, paidAt = "2024-06-01"),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        assertEquals(0, result.overpayments.size)
    }

    @Test
    fun noAllocationButHasPayment() {
        // 割当なし（items の payments に uid なし）だが支払いのみあるユーザー → 全額過払い
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 10000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 10000L, paidAt = "2024-06-01"),
                            PaymentRecord(uid = "u2", amount = 5000L, paidAt = "2024-06-01"),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        val u2 = result.overpayments.find { it.uid == "u2" }!!
        assertEquals(5000L, u2.overpaid) // allocated=0, paid=5000 → 全額過払い
        assertEquals(5000L, u2.net)
    }

    @Test
    fun multipleItemsSameMonthAccumulates() {
        // 同一月に複数 item がある場合の割当額累積
        val data =
            listOf(
                MonthlyMoney(
                    yearMonth = "2024-06",
                    items =
                        listOf(
                            MoneyItem(
                                id = "item1",
                                name = "Rent",
                                amount = 10000L,
                                payments = listOf(Payment(uid = "u1", amount = 4000L)),
                            ),
                            MoneyItem(
                                id = "item2",
                                name = "Utilities",
                                amount = 5000L,
                                payments = listOf(Payment(uid = "u1", amount = 3000L)),
                            ),
                        ),
                    paymentRecords =
                        listOf(
                            PaymentRecord(uid = "u1", amount = 8000L, paidAt = "2024-06-01"),
                        ),
                ),
            )
        val result = service.calculateOverpayments(data)
        // allocated = 4000 + 3000 = 7000, paid = 8000 → 1000 過払い
        val u1 = result.overpayments.find { it.uid == "u1" }!!
        assertEquals(1000L, u1.overpaid)
        assertEquals(1000L, u1.net)
    }
}
