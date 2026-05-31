package server.money

import model.MonthlyMoney

/** Money データのリポジトリインターフェース */
interface MoneyRepository {
    /** 月データを取得。ドキュメントが存在しない場合は null */
    suspend fun getMonthlyMoney(yearMonth: String): MonthlyMoney?

    suspend fun saveMonthlyMoney(
        yearMonth: String,
        data: MonthlyMoney,
    )

    /** targetYearMonth の前月から指定タグ付き項目を targetYearMonth にインポート（マージ）して返す */
    suspend fun importItemsByTag(
        targetYearMonth: String,
        tag: String,
    ): MonthlyMoney

    /** レポート用: 全月のデータを取得 */
    suspend fun getAllMonths(): List<MonthlyMoney>
}
