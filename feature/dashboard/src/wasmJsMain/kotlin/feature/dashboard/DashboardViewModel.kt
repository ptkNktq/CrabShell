@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.network.authenticatedClient
import core.ui.util.todayDateJs
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Feeding
import model.FeedingLog
import model.MealTime

@JsFun("(iso) => { const d = new Date(iso); return d.toLocaleTimeString('ja-JP', {hour:'2-digit', minute:'2-digit', hour12:false, timeZone:'Asia/Tokyo'}); }")
external fun toJstHHMM(iso: JsString): JsString

class DashboardViewModel(private val scope: CoroutineScope) {
    private val today: String = todayDateJs().toString()

    var feedingLog by mutableStateOf(FeedingLog(date = today))
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch { loadToday() }
    }

    private suspend fun loadToday() {
        try {
            feedingLog = authenticatedClient.get("/api/feeding/$today").body()
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    fun feed(mealTime: MealTime) {
        scope.launch {
            try {
                val feeding: Feeding = authenticatedClient.put("/api/feeding/$today/$mealTime").body()
                feedingLog = feedingLog.copy(
                    feedings = feedingLog.feedings + (mealTime to feeding)
                )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}
