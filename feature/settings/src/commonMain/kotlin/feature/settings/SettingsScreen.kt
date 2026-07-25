package feature.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.components.AdminBadge
import org.koin.compose.koinInject

internal enum class SettingsCategory(
    val title: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
) {
    // 全員向けセクション
    Account("アカウント", Icons.Default.Person),
    Credits("クレジット", Icons.Default.Copyright),

    // 管理者専用セクション
    UserManagement("ユーザー管理", Icons.Default.Group, adminOnly = true),
    Pet("ペット", Icons.Default.Pets, adminOnly = true),
    Garbage("ゴミ出し", Icons.Default.DeleteSweep, adminOnly = true),
    Quest("クエスト", Icons.Default.Star, adminOnly = true),
    Money("お金", Icons.Default.AccountBalance, adminOnly = true),
    Cache("サーバーキャッシュ", Icons.Default.Cached, adminOnly = true),
}

@Composable
fun SettingsScreen(
    categoryName: String? = null,
    onCategoryNameChange: (String?) -> Unit = {},
) {
    val authStateHolder = koinInject<AuthStateHolder>()
    val isAdmin = authStateHolder.isAdmin
    val selectedCategory =
        categoryName
            ?.let { name -> SettingsCategory.entries.find { it.name == name } }
            ?.takeIf { !it.adminOnly || isAdmin }
    val windowSizeClass = LocalWindowSizeClass.current

    SettingsContent(
        isAdmin = isAdmin,
        windowSizeClass = windowSizeClass,
        selectedCategory = selectedCategory,
        onSelectCategory = { onCategoryNameChange(it?.name) },
    )
}

@Composable
internal fun SettingsContent(
    isAdmin: Boolean,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
    selectedCategory: SettingsCategory? = null,
    onSelectCategory: (SettingsCategory?) -> Unit = {},
) {
    SettingsCategoryLayout(
        isAdmin = isAdmin,
        windowSizeClass = windowSizeClass,
        selectedCategory = selectedCategory,
        onSelectCategory = onSelectCategory,
        categoryContent = { category, cardModifier ->
            when (category) {
                SettingsCategory.Account -> AccountScreen(modifier = cardModifier)
                SettingsCategory.Credits -> CreditsScreen(modifier = cardModifier)
                SettingsCategory.UserManagement -> UserManagementScreen(modifier = cardModifier)
                SettingsCategory.Pet -> PetScreen(modifier = cardModifier)
                SettingsCategory.Garbage -> GarbageScreen(modifier = cardModifier)
                SettingsCategory.Quest -> QuestScreen(modifier = cardModifier)
                SettingsCategory.Money -> MoneyScreen(modifier = cardModifier)
                SettingsCategory.Cache -> CacheScreen(modifier = cardModifier)
            }
        },
    )
}

/**
 * [SettingsContent] のレイアウト骨格（一覧＋詳細ペインの配置ロジック）のみを切り出したもの。
 * 本番導線は常に [SettingsContent] 経由（categoryContent は実画面に固定）で、
 * このシグネチャを直接呼ぶのは各カテゴリの実際の見た目をKoinなしでレンダリングしたい
 * プレビュー・スクリーンショット生成のテストコードのみを想定している。
 */
@Composable
internal fun SettingsCategoryLayout(
    isAdmin: Boolean,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
    selectedCategory: SettingsCategory? = null,
    onSelectCategory: (SettingsCategory?) -> Unit = {},
    categoryContent: @Composable (SettingsCategory, Modifier) -> Unit,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact
    val categories = SettingsCategory.entries.filter { !it.adminOnly || isAdmin }

    if (isCompact) {
        val selected = selectedCategory
        if (selected == null) {
            CategoryListPane(
                categories = categories,
                selectedCategory = null,
                onSelectCategory = { onSelectCategory(it) },
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        } else {
            key(selected) {
                val detailScrollState = rememberScrollState()
                CategoryDetailPane(
                    category = selected,
                    scrollState = detailScrollState,
                    showBackButton = true,
                    onBack = { onSelectCategory(null) },
                    modifier = Modifier.fillMaxSize(),
                    contentModifier = Modifier.fillMaxWidth(),
                ) {
                    categoryContent(selected, Modifier.fillMaxWidth())
                }
            }
        }
    } else {
        val selected = selectedCategory ?: categories.firstOrNull()
        Row(modifier = Modifier.fillMaxSize()) {
            CategoryListPane(
                categories = categories,
                selectedCategory = selected,
                onSelectCategory = { onSelectCategory(it) },
                modifier = Modifier.width(320.dp).fillMaxHeight().padding(24.dp),
            )

            VerticalDivider()

            if (selected != null) {
                key(selected) {
                    val detailScrollState = rememberScrollState()
                    CategoryDetailPane(
                        category = selected,
                        scrollState = detailScrollState,
                        showBackButton = false,
                        onBack = {},
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentModifier = Modifier.widthIn(max = 480.dp),
                    ) {
                        categoryContent(selected, Modifier.widthIn(max = 480.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun settingsScrollbarStyle() =
    ScrollbarStyle(
        minimalHeight = 48.dp,
        thickness = 8.dp,
        shape = MaterialTheme.shapes.small,
        hoverDurationMillis = 300,
        unhoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )

@Composable
private fun CategoryListPane(
    categories: List<SettingsCategory>,
    selectedCategory: SettingsCategory?,
    onSelectCategory: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listScrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(listScrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            categories.forEach { category ->
                CategoryItem(
                    category = category,
                    isSelected = category == selectedCategory,
                    onClick = { onSelectCategory(category) },
                )
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = settingsScrollbarStyle(),
        )
    }
}

@Composable
private fun CategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = category.icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = category.title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
                if (category.adminOnly) AdminBadge()
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CategoryDetailPane(
    category: SettingsCategory,
    scrollState: ScrollState,
    showBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = contentModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (category.adminOnly) AdminBadge()
            }
            content()
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = settingsScrollbarStyle(),
        )
    }
}
