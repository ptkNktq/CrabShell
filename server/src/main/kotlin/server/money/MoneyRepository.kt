package server.money

import model.MoneyItem
import model.MonthlyMoney

/** Money データのリポジトリインターフェース */
interface MoneyRepository {
    /** 月データを取得。ドキュメントが存在しない場合は null */
    suspend fun getMonthlyMoney(month: String): MonthlyMoney?

    suspend fun saveMonthlyMoney(
        month: String,
        data: MonthlyMoney,
    )

    suspend fun getRecurringItemsFromPreviousMonth(month: String): List<MoneyItem>

    /** レポート用: 全月のデータを取得 */
    suspend fun getAllMonths(): List<MonthlyMoney>
}
