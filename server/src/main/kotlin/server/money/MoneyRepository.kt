package server.money

import model.MonthlyMoney

/** Money データのリポジトリインターフェース */
interface MoneyRepository {
    /** 月データを取得。ドキュメントが存在しない場合は null */
    suspend fun getMonthlyMoney(month: String): MonthlyMoney?

    suspend fun saveMonthlyMoney(
        month: String,
        data: MonthlyMoney,
    )

    /** targetMonth の前月から指定タグ付き項目を targetMonth にインポート（マージ）して返す */
    suspend fun importItemsByTag(
        targetMonth: String,
        tag: String,
    ): MonthlyMoney

    /** レポート用: 全月のデータを取得 */
    suspend fun getAllMonths(): List<MonthlyMoney>
}
