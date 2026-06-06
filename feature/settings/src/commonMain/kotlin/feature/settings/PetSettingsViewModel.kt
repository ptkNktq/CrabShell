package feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.common.FeedingSettingsChangedEvent
import core.network.FeedingSettingsRepository
import core.network.PetRepository
import kotlinx.coroutines.launch
import model.FeedingSettings
import model.MealTime
import model.Pet

/** どのテスト送信ボタンが処理中かを表す。null なら全ボタン待機中。 */
enum class FeedingTestPhase { SCHEDULED, REMINDER }

data class PetSettingsUiState(
    val isLoading: Boolean = true,
    val pets: List<Pet> = emptyList(),
    val editingPetNames: Map<String, String> = emptyMap(),
    val mealOrder: List<MealTime> = FeedingSettings.DEFAULT_MEAL_ORDER,
    val mealTimes: Map<MealTime, String> = emptyMap(),
    val reminderEnabled: Boolean = false,
    val reminderWebhookUrl: String = "",
    val reminderDelayMinutes: Int = 30,
    val reminderPrefix: String = "",
    // 保存状態・メッセージはカード単位で分離する（ペット名 / ごはん設定 / リマインダー）
    val petNameSaving: Boolean = false,
    val feedingSaving: Boolean = false,
    val reminderSaving: Boolean = false,
    val testingPhase: FeedingTestPhase? = null,
    val petNameMessage: String? = null,
    val feedingMessage: String? = null,
    val reminderMessage: String? = null,
)

class PetSettingsViewModel(
    private val petRepository: PetRepository,
    private val feedingSettingsRepository: FeedingSettingsRepository,
    private val feedingSettingsChangedEvent: FeedingSettingsChangedEvent,
) : ViewModel() {
    var uiState by mutableStateOf(PetSettingsUiState())
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val pets = petRepository.getPets()
                val settings = feedingSettingsRepository.getSettings()
                uiState =
                    uiState.copy(
                        isLoading = false,
                        pets = pets,
                        editingPetNames = pets.associate { it.id to it.name },
                        mealOrder = settings.mealOrder,
                        mealTimes = settings.mealTimes,
                        reminderEnabled = settings.reminderEnabled,
                        reminderWebhookUrl = settings.reminderWebhookUrl,
                        reminderDelayMinutes = settings.reminderDelayMinutes,
                        reminderPrefix = settings.reminderPrefix,
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, feedingMessage = "読み込み失敗: ${e.message}")
            }
        }
    }

    fun onPetNameChanged(
        petId: String,
        name: String,
    ) {
        uiState =
            uiState.copy(
                editingPetNames = uiState.editingPetNames + (petId to name),
                petNameMessage = null,
            )
    }

    fun onSavePetName(petId: String) {
        val name = uiState.editingPetNames[petId] ?: return
        uiState = uiState.copy(petNameSaving = true, petNameMessage = null)
        viewModelScope.launch {
            try {
                val updated = petRepository.updatePetName(petId, name)
                uiState =
                    uiState.copy(
                        petNameSaving = false,
                        pets = uiState.pets.map { if (it.id == petId) updated else it },
                        petNameMessage = "ペット名を更新しました",
                    )
            } catch (e: Exception) {
                uiState = uiState.copy(petNameSaving = false, petNameMessage = "更新失敗: ${e.message}")
            }
        }
    }

    fun onMealOrderChanged(order: List<MealTime>) {
        uiState = uiState.copy(mealOrder = order, feedingMessage = null)
    }

    fun onMealTimeChanged(
        mealTime: MealTime,
        time: String,
    ) {
        uiState =
            uiState.copy(
                mealTimes = uiState.mealTimes + (mealTime to time),
                feedingMessage = null,
            )
    }

    fun onReminderEnabledChanged(enabled: Boolean) {
        uiState = uiState.copy(reminderEnabled = enabled, reminderMessage = null)
    }

    fun onReminderWebhookUrlChanged(url: String) {
        uiState = uiState.copy(reminderWebhookUrl = url, reminderMessage = null)
    }

    fun onReminderDelayMinutesChanged(minutes: Int) {
        uiState = uiState.copy(reminderDelayMinutes = minutes.coerceIn(1, 180), reminderMessage = null)
    }

    fun onReminderPrefixChanged(prefix: String) {
        uiState = uiState.copy(reminderPrefix = prefix, reminderMessage = null)
    }

    private fun validateMealTimes(mealTimes: Map<MealTime, String>): Map<MealTime, String> =
        mealTimes.mapValues { (_, time) ->
            val parts = time.split(":")
            val hour = (parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0).coerceIn(0, 23)
            val minute = (parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0).coerceIn(0, 59)
            "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }

    /**
     * ごはん設定（表示順・予定時刻）のみを保存する。
     *
     * 保存直前にサーバーから最新の [FeedingSettings] を取得し、ごはん設定フィールドだけ [FeedingSettings.copy]
     * で上書きしてから全体を PUT する。これにより、リマインダーカード側で編集中（未保存）の値を巻き込まずに済む。
     *
     * 取得した最新値は PUT を組み立てるためだけに使い、[uiState] には書き戻さない（保存対象外フィールドの
     * 同期はしない）。書き戻すと他カードで編集中（未保存）の値が消えてしまうため、ローカルの編集状態を優先する。
     */
    fun onSaveFeeding() {
        val validatedMealTimes = validateMealTimes(uiState.mealTimes)
        uiState = uiState.copy(feedingSaving = true, feedingMessage = null, mealTimes = validatedMealTimes)
        viewModelScope.launch {
            try {
                val merged =
                    feedingSettingsRepository.getSettings().copy(
                        mealOrder = uiState.mealOrder,
                        mealTimes = validatedMealTimes,
                    )
                feedingSettingsRepository.updateSettings(merged)
                uiState = uiState.copy(feedingSaving = false, feedingMessage = "設定を保存しました")
                feedingSettingsChangedEvent.emit()
            } catch (e: Exception) {
                uiState = uiState.copy(feedingSaving = false, feedingMessage = "保存失敗: ${e.message}")
            }
        }
    }

    /**
     * リマインダー設定のみを保存する。
     *
     * 保存直前にサーバーから最新の [FeedingSettings] を取得し、リマインダーフィールドだけ [FeedingSettings.copy]
     * で上書きしてから全体を PUT する。これにより、ごはん設定カード側で編集中（未保存）の値を巻き込まずに済む。
     *
     * 取得した最新値は PUT を組み立てるためだけに使い、[uiState] には書き戻さない（保存対象外フィールドの
     * 同期はしない）。書き戻すと他カードで編集中（未保存）の値が消えてしまうため、ローカルの編集状態を優先する。
     */
    fun onSaveReminder() {
        uiState = uiState.copy(reminderSaving = true, reminderMessage = null)
        viewModelScope.launch {
            try {
                val merged =
                    feedingSettingsRepository.getSettings().copy(
                        reminderEnabled = uiState.reminderEnabled,
                        reminderWebhookUrl = uiState.reminderWebhookUrl,
                        reminderDelayMinutes = uiState.reminderDelayMinutes,
                        reminderPrefix = uiState.reminderPrefix,
                    )
                feedingSettingsRepository.updateSettings(merged)
                uiState = uiState.copy(reminderSaving = false, reminderMessage = "設定を保存しました")
                feedingSettingsChangedEvent.emit()
            } catch (e: Exception) {
                uiState = uiState.copy(reminderSaving = false, reminderMessage = "保存失敗: ${e.message}")
            }
        }
    }

    fun onTestScheduled() {
        uiState = uiState.copy(testingPhase = FeedingTestPhase.SCHEDULED, reminderMessage = null)
        viewModelScope.launch {
            try {
                feedingSettingsRepository.testScheduled()
                uiState = uiState.copy(testingPhase = null, reminderMessage = "定刻通知をテスト送信しました")
            } catch (e: Exception) {
                uiState = uiState.copy(testingPhase = null, reminderMessage = "テスト送信失敗: ${e.message}")
            }
        }
    }

    fun onTestReminder() {
        uiState = uiState.copy(testingPhase = FeedingTestPhase.REMINDER, reminderMessage = null)
        viewModelScope.launch {
            try {
                feedingSettingsRepository.testReminder()
                uiState = uiState.copy(testingPhase = null, reminderMessage = "リマインダーをテスト送信しました")
            } catch (e: Exception) {
                uiState = uiState.copy(testingPhase = null, reminderMessage = "テスト送信失敗: ${e.message}")
            }
        }
    }
}
