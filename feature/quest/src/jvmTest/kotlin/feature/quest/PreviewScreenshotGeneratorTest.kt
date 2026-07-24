package feature.quest

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import model.Quest
import model.QuestCategory
import model.UserPoints
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * @Preview 本導入の判断材料として、commonMain 化済みの Content composable を
 * JVM ターゲット上でオフスクリーンレンダリングし、3パターンの画面サイズで PNG 出力する。
 * 手動実行専用（previewScreenshotTest タスク）で、通常の jvmTest/CI には含めない。
 */
@OptIn(ExperimentalComposeUiApi::class)
class PreviewScreenshotGeneratorTest {
    private val outputDir =
        File("build/preview-screenshots").apply { mkdirs() }

    private data class SizePattern(
        val label: String,
        val width: Int,
        val height: Int,
        val windowSizeClass: WindowSizeClass,
    )

    private val sizePatterns =
        listOf(
            SizePattern("compact", 375, 800, WindowSizeClass.Compact),
            SizePattern("medium", 700, 800, WindowSizeClass.Medium),
            SizePattern("expanded", 1000, 800, WindowSizeClass.Expanded),
        )

    private fun saveScreenshot(
        fileName: String,
        width: Int,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(width = width, height = height) { content() }
        try {
            // 1回目の render() で LaunchedEffect 等の副作用・アニメーションの初期状態を確定させ、
            // 2回目の render() で確定した見た目を取得する（ImageComposeScene の既知のウォームアップパターン）。
            scene.render()
            val image = scene.render()
            val bytes = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encode failed" }.bytes
            File(outputDir, fileName).writeBytes(bytes)
        } finally {
            scene.close()
        }
    }

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

        sizePatterns.forEach { pattern ->
            saveScreenshot(
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
