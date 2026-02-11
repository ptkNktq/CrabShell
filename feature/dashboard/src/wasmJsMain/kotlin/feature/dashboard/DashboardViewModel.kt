package feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.network.authenticatedClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.DashboardItem

class DashboardViewModel(scope: CoroutineScope) {
    var items by mutableStateOf<List<DashboardItem>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch { loadItems() }
    }

    private suspend fun loadItems() {
        try {
            items = authenticatedClient.get("/api/items").body()
            loading = false
        } catch (e: Exception) {
            error = e.message
            loading = false
        }
    }
}
