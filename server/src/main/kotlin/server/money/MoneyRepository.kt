package server.money

import server.money.model.MonthlyMoneyRecord

/** Money データのリポジトリインターフェース */
interface MoneyRepository {
    /** 月データを取得。ドキュメントが存在しない場合は null */
    suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoneyRecord?

    suspend fun saveMonthlyMoney(
        yearMonth: String,
        data: MonthlyMoneyRecord,
    )

    /** targetYearMonth の前月から指定タグ付き項目を targetYearMonth にインポート（マージ）して返す */
    suspend fun importItemsByTag(
        targetYearMonth: String,
        tag: String,
    ): MonthlyMoneyRecord

    /** レポート用: 全月のデータを取得 */
    suspend fun getAllMonths(): List<MonthlyMoneyRecord>
}
