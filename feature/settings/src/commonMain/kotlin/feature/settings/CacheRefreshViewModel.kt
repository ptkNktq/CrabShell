package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.network.CacheRepository
import kotlinx.coroutines.launch

data class CacheRefreshUiState(
    val isClearing: Boolean = false,
    val message: String? = null,
)

class CacheRefreshViewModel(
    private val cacheRepository: CacheRepository,
) : ViewModel() {
    var uiState by mutableStateOf(CacheRefreshUiState())
        private set

    fun onClearCache() {
        uiState = uiState.copy(isClearing = true, message = null)
        viewModelScope.launch {
            try {
                val result = cacheRepository.clearServerCache()
                uiState = uiState.copy(isClearing = false, message = result.message)
            } catch (e: Exception) {
                uiState = uiState.copy(isClearing = false, message = "キャッシュクリア失敗: ${e.message}")
            }
        }
    }
}
