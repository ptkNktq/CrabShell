@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.payment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.auth.toJsString
import core.network.authenticatedClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.MoneyItem
import model.MonthlyMoney
import model.PaymentRecord

/** 現在の年月を "YYYY-MM" 形式で返す */
@JsFun("""() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    return y + '-' + m;
}""")
external fun currentMonthJs(): JsString

/** 月を offset 分ずらす */
@JsFun("""(monthStr, offset) => {
    const [y, m] = monthStr.split('-').map(Number);
    const d = new Date(y, m - 1 + offset, 1);
    const ny = d.getFullYear();
    const nm = String(d.getMonth() + 1).padStart(2, '0');
    return ny + '-' + nm;
}""")
external fun shiftMonthJs(monthStr: JsString, offset: Int): JsString

/** 現在の日時を ISO 形式で返す */
@JsFun("() => new Date().toISOString()")
external fun nowIsoJs(): JsString

class PaymentViewModel(private val scope: CoroutineScope) {
    var currentMonth by mutableStateOf(currentMonthJs().toString())
        private set
    var monthlyMoney by mutableStateOf(MonthlyMoney(month = currentMonth))
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // 支払い記録ダイアログ
    var payingItem by mutableStateOf<MoneyItem?>(null)
        private set

    val currentUid: String
        get() = (AuthStateHolder.state as? AuthState.Authenticated)?.user?.uid ?: ""

    init {
        loadMonth(currentMonth)
    }

    fun loadMonth(month: String) {
        currentMonth = month
        loading = true
        error = null
        scope.launch {
            try {
                monthlyMoney = authenticatedClient.get("/api/money/$month/my").body()
                loading = false
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    fun goToPreviousMonth() {
        loadMonth(shiftMonthJs(currentMonth.toJsString(), -1).toString())
    }

    fun goToNextMonth() {
        loadMonth(shiftMonthJs(currentMonth.toJsString(), 1).toString())
    }

    fun openPayDialog(item: MoneyItem) {
        payingItem = item
    }

    fun closePayDialog() {
        payingItem = null
    }

    fun recordPayment(item: MoneyItem, amount: Long) {
        closePayDialog()
        scope.launch {
            try {
                val record = PaymentRecord(amount = amount, paidAt = nowIsoJs().toString())
                val updated: MonthlyMoney = authenticatedClient.post("/api/money/$currentMonth/items/${item.id}/pay") {
                    contentType(ContentType.Application.Json)
                    setBody(record)
                }.body()
                // 自分の割当のみフィルタ
                val uid = currentUid
                monthlyMoney = updated.copy(
                    items = updated.items.filter { it.payments.any { p -> p.uid == uid } }
                )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}
