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
import model.Pet

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
    var pet by mutableStateOf<Pet?>(null)
        private set

    init {
        scope.launch {
            try {
                val pets: List<Pet> = authenticatedClient.get("/api/pets").body()
                pet = pets.firstOrNull()
                loadToday()
            } catch (e: Exception) {
                error = e.message
                loading = false
            }
        }
    }

    private suspend fun loadToday() {
        val petId = pet?.id ?: return
        try {
            feedingLog = authenticatedClient.get("/api/pets/$petId/feeding/$today").body()
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }

    fun feed(mealTime: MealTime) {
        val petId = pet?.id ?: return
        scope.launch {
            try {
                val feeding: Feeding = authenticatedClient.put(
                    "/api/pets/$petId/feeding/$today/${mealTime.name.lowercase()}"
                ).body()
                feedingLog = feedingLog.copy(
                    feedings = feedingLog.feedings + (mealTime to feeding)
                )
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
}
