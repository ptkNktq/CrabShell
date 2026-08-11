package feature.feeding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import core.common.AppLogger
import core.common.FeedingSettingsChangedEvent
import core.common.TabResumedEvent
import core.network.FeedingRepository
import core.network.FeedingSettingsRepository
import core.network.PetRepository
import core.ui.util.dateWithHourBoundary
import core.ui.util.shiftDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import model.FeedingLog
import model.FeedingSettings
import model.MealTime
import model.Pet

data class FeedingUiState(
    val log: FeedingLog = FeedingLog(date = ""),
    val selectedDate: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val noteDraft: String = "",
    val pet: Pet? = null,
    val editingMealTime: MealTime? = null,
    val mealOrder: List<MealTime> = FeedingSettings.DEFAULT_MEAL_ORDER,
    val feedingInProgress: MealTime? = null,
    val isSavingNote: Boolean = false,
    val isSavingTimestamp: Boolean = false,
)

class FeedingViewModel(
    tabResumedEvent: TabResumedEvent,
    feedingSettingsChangedEvent: FeedingSettingsChangedEvent,
    private val petRepository: PetRepository,
    private val feedingRepository: FeedingRepository,
    private val feedingSettingsRepository: FeedingSettingsRepository,
) : ViewModel() {
    var uiState by mutableStateOf(
        FeedingUiState(
            log = FeedingLog(date = dateWithHourBoundary()),
            selectedDate = dateWithHourBoundary(),
        ),
    )
        private set

    private var feedingSettingsJob: Job? = null
    private var loadLogJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val pet = petRepository.getPets().firstOrNull()
                uiState = uiState.copy(pet = pet)
                onLoadLog(uiState.selectedDate)
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message, isLoading = false)
            }
        }
        loadFeedingSettings()
        // タブ復帰時: 設定変更の反映に加え、バックグラウンドで経過した分を補うため
        // 選択中日付のログも再取得する（Dashboard の onRefreshFeeding と対称）
        viewModelScope.launch {
            tabResumedEvent.events.collect {
                loadFeedingSettings()
                onLoadLog(uiState.selectedDate)
            }
        }
        // 設定画面で保存された瞬間、同一タブ内でも mealOrder を即時反映する
        viewModelScope.launch {
            feedingSettingsChangedEvent.events.collect {
                loadFeedingSettings()
            }
        }
    }

    private fun loadFeedingSettings() {
        feedingSettingsJob?.cancel()
        feedingSettingsJob =
            viewModelScope.launch {
                try {
                    val settings = feedingSettingsRepository.getSettings()
                    uiState = uiState.copy(mealOrder = settings.mealOrder)
                } catch (e: CancellationException) {
                    // 次回 load の cancel による正常中断は失敗扱いにしない
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "feeding settings load failed: ${e.message}")
                }
            }
    }

    /** 日送り連打時は前のリクエストをキャンセルし、最後の操作の結果のみ反映する */
    fun onLoadLog(date: String) {
        val petId = uiState.pet?.id ?: return
        uiState = uiState.copy(selectedDate = date, isLoading = true, error = null)
        loadLogJob?.cancel()
        loadLogJob =
            viewModelScope.launch {
                try {
                    val log = feedingRepository.getFeedingLog(petId, date)
                    uiState = uiState.copy(log = log, noteDraft = log.note, isLoading = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    uiState = uiState.copy(error = e.message, isLoading = false)
                }
            }
    }

    fun onFeed(mealTime: MealTime) {
        val petId = uiState.pet?.id ?: return
        val date = uiState.selectedDate
        uiState = uiState.copy(feedingInProgress = mealTime)
        viewModelScope.launch {
            try {
                val feeding = feedingRepository.feed(petId, date, mealTime)
                // 給餌 API 実行中に日送りされた場合、古い日付の結果を表示中ログへ混ぜない
                if (uiState.selectedDate == date) {
                    uiState =
                        uiState.copy(
                            log =
                                uiState.log.copy(
                                    feedings =
                                        uiState.log.feedings
                                            .toMutableMap()
                                            .apply { put(mealTime, feeding) },
                                ),
                        )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(feedingInProgress = null)
            }
        }
    }

    fun onUpdateNoteDraft(text: String) {
        uiState = uiState.copy(noteDraft = text)
    }

    fun onSaveNote() {
        val petId = uiState.pet?.id ?: return
        val date = uiState.selectedDate
        val note = uiState.noteDraft
        uiState = uiState.copy(isSavingNote = true)
        viewModelScope.launch {
            try {
                feedingRepository.updateNote(petId, date, note)
                // 保存 API 実行中に日送りされた場合、古い日付の結果を表示中ログへ混ぜない
                if (uiState.selectedDate == date) {
                    uiState = uiState.copy(log = uiState.log.copy(note = note))
                }
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSavingNote = false)
            }
        }
    }

    fun onStartEditTimestamp(mealTime: MealTime) {
        uiState = uiState.copy(editingMealTime = mealTime)
    }

    fun onCancelEditTimestamp() {
        uiState = uiState.copy(editingMealTime = null)
    }

    fun onSaveTimestamp(
        mealTime: MealTime,
        hour: Int,
        minute: Int,
    ) {
        val petId = uiState.pet?.id ?: return
        val date = uiState.selectedDate
        val timestamp = "${date}T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00+09:00"
        uiState = uiState.copy(isSavingTimestamp = true)
        viewModelScope.launch {
            try {
                val feeding =
                    feedingRepository.updateFeedingTimestamp(
                        petId,
                        date,
                        mealTime,
                        timestamp,
                    )
                // 保存 API 実行中に日送りされた場合、古い日付の結果を表示中ログへ混ぜない
                if (uiState.selectedDate == date) {
                    uiState =
                        uiState.copy(
                            editingMealTime = null,
                            log =
                                uiState.log.copy(
                                    feedings =
                                        uiState.log.feedings
                                            .toMutableMap()
                                            .apply { put(mealTime, feeding) },
                                ),
                        )
                } else {
                    uiState = uiState.copy(editingMealTime = null)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(error = e.message)
            } finally {
                uiState = uiState.copy(isSavingTimestamp = false)
            }
        }
    }

    fun onGoToPreviousDay() {
        onLoadLog(shiftDate(uiState.selectedDate, -1))
    }

    fun onGoToNextDay() {
        onLoadLog(shiftDate(uiState.selectedDate, 1))
    }

    companion object {
        private const val TAG = "FeedingViewModel"
    }
}
