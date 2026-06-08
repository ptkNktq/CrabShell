package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import core.common.AppLogger
import feature.settings.generated.Res
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi

data class LicensesUiState(
    val isLoading: Boolean = true,
    val libraries: List<Library> = emptyList(),
    val error: String? = null,
)

class LicensesViewModel : ViewModel() {
    var uiState by mutableStateOf(LicensesUiState())
        private set

    init {
        loadLicenses()
    }

    @OptIn(ExperimentalResourceApi::class)
    fun loadLicenses() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val json = Res.readBytes("files/aboutlibraries.json").decodeToString()
                val libs = Libs.Builder().withJson(json).build()
                uiState = LicensesUiState(isLoading = false, libraries = libs.libraries)
            } catch (e: Exception) {
                AppLogger.e("LicensesViewModel", "Failed to load licenses: ${e.message}")
                uiState =
                    LicensesUiState(
                        isLoading = false,
                        error = e.message ?: "ライセンス情報の読み込みに失敗しました",
                    )
            }
        }
    }
}
