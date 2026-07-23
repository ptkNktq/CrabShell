package feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import core.ui.WindowSizeClass
import core.ui.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * @Preview 本導入の判断材料として、commonMain 化済みの settings 全8カテゴリ画面を
 * JVM ターゲット上でオフスクリーンレンダリングし、3パターンの画面サイズで
 * PNG 出力する PoC。Android ターゲットが無いため IDE のプレビューパネルは
 * 使えないので、ここでは自動スクショ生成のみを検証する。
 *
 * 各カテゴリの実際の Content コンポーネントに、各 ViewModel の UiState の
 * デフォルト値（isLoading = false のロード済み・空状態）を渡してレンダリングする。
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
            scene.render()
            val image = scene.render()
            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outputDir, fileName).writeBytes(bytes)
        } finally {
            scene.close()
        }
    }

    // 各カテゴリの実際の Content を、各 ViewModel の UiState のデフォルト値（ロード済み・空状態）で描画する。
    // koinViewModel() には触れないため Koin コンテキスト不要。
    @Composable
    private fun RealCategoryContent(
        category: SettingsCategory,
        modifier: Modifier,
    ) {
        when (category) {
            SettingsCategory.Account ->
                AccountContent(
                    passwordState = PasswordChangeUiState(),
                    passkeyState = PasskeyManagementUiState(isLoading = false, isAvailable = true, credentialCount = 1),
                    loginHistoryState = LoginHistoryUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.Credits ->
                CreditsContent(
                    state = LicensesUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.UserManagement ->
                UserManagementContent(
                    state = UserNameUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.Pet ->
                PetContent(
                    state = PetSettingsUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.Garbage ->
                GarbageContent(
                    state = GarbageScheduleUiState(isLoading = false, notificationLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.Quest ->
                QuestContent(
                    state = QuestWebhookUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.Money ->
                MoneyContent(
                    moneyState = MoneyWebhookUiState(isLoading = false),
                    paymentState = PaymentWebhookUiState(isLoading = false),
                    modifier = modifier,
                )
            SettingsCategory.Cache ->
                CacheContent(
                    state = CacheRefreshUiState(),
                    modifier = modifier,
                )
        }
    }

    @Test
    fun generateAllCategoryScreenshots() {
        sizePatterns.forEach { pattern ->
            SettingsCategory.entries.forEach { category ->
                saveScreenshot(
                    fileName = "settings_${category.name.lowercase()}_${pattern.label}.png",
                    width = pattern.width,
                    height = pattern.height,
                ) {
                    AppTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            SettingsCategoryLayout(
                                isAdmin = true,
                                windowSizeClass = pattern.windowSizeClass,
                                selectedCategory = category,
                                categoryContent = { cat, mod -> RealCategoryContent(cat, mod) },
                            )
                        }
                    }
                }
            }
        }
    }
}
