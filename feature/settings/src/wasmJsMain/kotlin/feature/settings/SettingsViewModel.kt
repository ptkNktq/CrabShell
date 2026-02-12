package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.auth.AuthRepository
import core.network.authenticatedClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.CollectionFrequency
import model.GarbageType
import model.GarbageTypeSchedule

class SettingsViewModel(private val scope: CoroutineScope) {
    // パスワード変更
    var currentPassword by mutableStateOf("")
        private set
    var newPassword by mutableStateOf("")
        private set
    var confirmPassword by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    // ゴミ出しスケジュール
    var garbageSchedules by mutableStateOf(
        GarbageType.entries.map { GarbageTypeSchedule(garbageType = it, daysOfWeek = emptyList()) }
    )
        private set
    var garbageLoading by mutableStateOf(true)
        private set
    var garbageSaving by mutableStateOf(false)
        private set
    var garbageMessage by mutableStateOf<String?>(null)
        private set

    init {
        loadGarbageSchedule()
    }

    // --- パスワード変更 ---

    fun onCurrentPasswordChanged(value: String) {
        currentPassword = value
        errorMessage = null
        successMessage = null
    }

    fun onNewPasswordChanged(value: String) {
        newPassword = value
        errorMessage = null
        successMessage = null
    }

    fun onConfirmPasswordChanged(value: String) {
        confirmPassword = value
        errorMessage = null
        successMessage = null
    }

    fun changePassword() {
        if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            errorMessage = "すべての項目を入力してください"
            return
        }
        if (newPassword != confirmPassword) {
            errorMessage = "新しいパスワードが一致しません"
            return
        }
        if (newPassword.length < 6) {
            errorMessage = "パスワードは6文字以上で入力してください"
            return
        }

        isLoading = true
        errorMessage = null
        successMessage = null
        scope.launch {
            val result = AuthRepository.changePassword(currentPassword, newPassword)
            isLoading = false
            if (result.isSuccess) {
                successMessage = "パスワードを変更しました"
                currentPassword = ""
                newPassword = ""
                confirmPassword = ""
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "パスワードの変更に失敗しました"
            }
        }
    }

    // --- ゴミ出しスケジュール ---

    private fun loadGarbageSchedule() {
        scope.launch {
            try {
                val loaded: List<GarbageTypeSchedule> =
                    authenticatedClient.get("/api/garbage/schedule").body()
                garbageSchedules = GarbageType.entries.map { type ->
                    loaded.find { it.garbageType == type }
                        ?: GarbageTypeSchedule(garbageType = type, daysOfWeek = emptyList())
                }
            } catch (_: Exception) {
                // 初回は空でOK
            } finally {
                garbageLoading = false
            }
        }
    }

    fun toggleDay(garbageType: GarbageType, dayIndex: Int) {
        garbageMessage = null
        garbageSchedules = garbageSchedules.map { schedule ->
            if (schedule.garbageType == garbageType) {
                val newDays = if (dayIndex in schedule.daysOfWeek) {
                    schedule.daysOfWeek - dayIndex
                } else {
                    schedule.daysOfWeek + dayIndex
                }
                schedule.copy(daysOfWeek = newDays.sorted())
            } else {
                schedule
            }
        }
    }

    fun changeFrequency(garbageType: GarbageType, frequency: CollectionFrequency) {
        garbageMessage = null
        garbageSchedules = garbageSchedules.map { schedule ->
            if (schedule.garbageType == garbageType) {
                schedule.copy(frequency = frequency)
            } else {
                schedule
            }
        }
    }

    fun saveGarbageSchedule() {
        garbageSaving = true
        garbageMessage = null
        scope.launch {
            try {
                authenticatedClient.put("/api/garbage/schedule") {
                    contentType(ContentType.Application.Json)
                    setBody(garbageSchedules)
                }
                garbageMessage = "保存しました"
            } catch (e: Exception) {
                garbageMessage = "保存に失敗しました: ${e.message}"
            } finally {
                garbageSaving = false
            }
        }
    }
}
