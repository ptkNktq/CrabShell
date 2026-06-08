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

    /**
     * 支払い記録を物理削除する。
     * [paymentId] が一致し [uid] が所有者である Payment を除外して保存する。
     * 対象の月・Payment が存在しない場合は null を返す。
     */
    suspend fun deletePayment(
        yearMonth: String,
        uid: String,
        paymentId: String,
    ): MonthlyMoney?
}
