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

/** 現在の日時を JST の ISO 形式で返す */
@JsFun("""() => {
    const now = new Date();
    const jst = new Date(now.getTime() + 9 * 60 * 60 * 1000);
    return jst.toISOString().slice(0, -1) + '+09:00';
}""")
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
    var saving by mutableStateOf(false)
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

    fun recordPayment(amount: Long) {
        saving = true
        scope.launch {
            try {
                val record = PaymentRecord(uid = currentUid, amount = amount, paidAt = nowIsoJs().toString())
                monthlyMoney = authenticatedClient.post("/api/money/$currentMonth/pay") {
                    contentType(ContentType.Application.Json)
                    setBody(record)
                }.body()
            } catch (e: Exception) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }
}
