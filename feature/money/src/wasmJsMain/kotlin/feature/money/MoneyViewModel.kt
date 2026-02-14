@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.toJsString
import core.network.authenticatedClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.User

/** 現在の年月を "YYYY-MM" 形式で返す */
@JsFun("""() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    return y + '-' + m;
}""")
external fun currentMonthJs(): JsString

/** 月を offset 分ずらす (例: "2026-02", 1 → "2026-03") */
@JsFun("""(monthStr, offset) => {
    const [y, m] = monthStr.split('-').map(Number);
    const d = new Date(y, m - 1 + offset, 1);
    const ny = d.getFullYear();
    const nm = String(d.getMonth() + 1).padStart(2, '0');
    return ny + '-' + nm;
}""")
external fun shiftMonthJs(monthStr: JsString, offset: Int): JsString

/** crypto.randomUUID() で UUID を生成 */
@JsFun("() => crypto.randomUUID()")
external fun randomUUID(): JsString

class MoneyViewModel(private val scope: CoroutineScope) {
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

    // ユーザー一覧
    var users by mutableStateOf<List<User>>(emptyList())
        private set

    // ダイアログ状態
    var editingItem by mutableStateOf<MoneyItem?>(null)
        private set
    var showDialog by mutableStateOf(false)
        private set

    init {
        loadUsers()
        loadMonth(currentMonth)
    }

    private fun loadUsers() {
        scope.launch {
            try {
                users = authenticatedClient.get("/api/users").body()
            } catch (_: Exception) {
                // ユーザー一覧取得失敗は無視
            }
        }
    }

    fun loadMonth(month: String) {
        currentMonth = month
        loading = true
        error = null
        scope.launch {
            try {
                monthlyMoney = authenticatedClient.get("/api/money/$month").body()
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

    fun openAddDialog() {
        editingItem = null
        showDialog = true
    }

    fun openEditDialog(item: MoneyItem) {
        editingItem = item
        showDialog = true
    }

    fun closeDialog() {
        showDialog = false
        editingItem = null
    }

    fun saveItem(name: String, amount: Long, note: String, payments: List<Payment>) {
        val existing = editingItem
        val newItem = if (existing != null) {
            existing.copy(name = name, amount = amount, note = note, payments = payments)
        } else {
            MoneyItem(
                id = randomUUID().toString(),
                name = name,
                amount = amount,
                note = note,
                payments = payments,
            )
        }

        val updatedItems = if (existing != null) {
            monthlyMoney.items.map { if (it.id == existing.id) newItem else it }
        } else {
            monthlyMoney.items + newItem
        }

        saveMonthlyMoney(monthlyMoney.copy(items = updatedItems))
        closeDialog()
    }

    fun deleteItem(item: MoneyItem) {
        val updatedItems = monthlyMoney.items.filter { it.id != item.id }
        saveMonthlyMoney(monthlyMoney.copy(items = updatedItems))
    }

    private fun saveMonthlyMoney(data: MonthlyMoney) {
        saving = true
        monthlyMoney = data
        scope.launch {
            try {
                authenticatedClient.put("/api/money/${data.month}") {
                    contentType(ContentType.Application.Json)
                    setBody(data)
                }
                saving = false
            } catch (e: Exception) {
                error = e.message
                saving = false
            }
        }
    }
}
