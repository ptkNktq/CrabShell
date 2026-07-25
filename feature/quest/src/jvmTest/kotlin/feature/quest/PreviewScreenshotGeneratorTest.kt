package feature.quest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import core.previewscreenshot.PreviewScreenshotRecorder
import core.previewscreenshot.standardSizePatterns
import core.ui.theme.AppTheme
import model.Quest
import model.QuestCategory
import model.UserPoints
import kotlin.test.Test

/**
 * commonMain 化済みの Content composable を JVM ターゲット上でオフスクリーンレンダリングし、
 * 3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
class PreviewScreenshotGeneratorTest {
    // previewScreenshotTest タスク経由の実行時は convention plugin が注入する。
    // IDE から直接実行する場合の未設定時はこのモジュール名にフォールバックする。
    private val recorder = PreviewScreenshotRecorder(System.getProperty("previewScreenshot.module") ?: "quest")

    @Test
    fun generateQuestScreenshots() {
        val quests =
            listOf(
                Quest(
                    id = "q1",
                    title = "洗濯物を干す",
                    description = "王国の衣を清め、日の恵みで乾かすべし",
                    category = QuestCategory.Housework,
                    rewardPoints = 10,
                    creatorUid = "user1",
                    creatorName = "たろう",
                ),
            )
        val myPoints = UserPoints(uid = "user1", displayName = "たろう", balance = 120)

        standardSizePatterns.forEach { pattern ->
            recorder.save(
                fileName = "quest_${pattern.label}.png",
                width = pattern.width,
                height = pattern.height,
            ) {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        QuestBoardContent(
                            quests = quests,
                            isLoading = false,
                            error = null,
                            isCreating = false,
                            canCreateQuest = true,
                            isAiAvailable = false,
                            isGenerating = false,
                            currentUserUid = "user1",
                            currentTab = QuestTab.Board,
                            myPoints = myPoints,
                            rewards = emptyList(),
                            history = emptyList(),
                            isAdmin = true,
                            isCreatingReward = false,
                            onSelectTab = {},
                            onToggleCreateForm = {},
                            onCreateQuest = { _, _, _, _, _ -> },
                            onGenerateText = { _, _, _, _, _, _, _ -> },
                            onAcceptQuest = {},
                            onVerifyQuest = {},
                            onDeleteQuest = {},
                            onExchangeReward = {},
                            onToggleCreateReward = {},
                            onCreateReward = { _, _, _ -> },
                            onDeleteReward = {},
                            onDismissError = {},
                            windowSizeClass = pattern.windowSizeClass,
                        )
                    }
                }
            }
        }
    }
}
