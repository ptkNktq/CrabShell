package feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.common.addPageVisibleListener
import core.common.removePageVisibleListener
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.extensions.FeedingDoneColor
import core.ui.extensions.color
import core.ui.extensions.displayOrder
import core.ui.extensions.icon
import core.ui.extensions.label
import core.ui.theme.displayExLarge
import core.ui.util.toJstHHMM
import model.FeedingLog
import model.GarbageType
import model.MealTime
import org.koin.compose.viewmodel.koinViewModel

private val CardHeaderMinHeight = 48.dp

@Composable
fun DashboardScreen(vm: DashboardViewModel = koinViewModel()) {
    // バックグラウンドからの復帰時にトークンリフレッシュ+データ再取得
    DisposableEffect(Unit) {
        val handler = addPageVisibleListener { vm.onTabResumed() }
        onDispose { removePageVisibleListener(handler) }
    }

    val windowSizeClass = LocalWindowSizeClass.current

    DashboardContent(
        feedingLoading = vm.uiState.feedingLoading,
        feedingError = vm.uiState.feedingError,
        feedingActionError = vm.uiState.feedingActionError,
        feedingLog = vm.uiState.feedingLog,
        petName = vm.uiState.petName,
        todayGarbageTypes = vm.uiState.todayGarbageTypes,
        garbageUpdateLabel = vm.uiState.garbageUpdateLabel,
        currentTime = vm.uiState.currentTime,
        currentYear = vm.uiState.currentYear,
        dateWithDay = vm.uiState.dateWithDay,
        onFeedClick = vm::onFeed,
        onRefreshFeeding = vm::onRefreshFeeding,
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun DashboardContent(
    feedingLoading: Boolean,
    feedingError: String?,
    feedingActionError: String?,
    feedingLog: FeedingLog,
    petName: String?,
    todayGarbageTypes: List<GarbageType>,
    garbageUpdateLabel: String,
    currentTime: String,
    currentYear: String,
    dateWithDay: String,
    onFeedClick: (MealTime) -> Unit,
    onRefreshFeeding: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    Column(
        modifier =
            Modifier.fillMaxSize().padding(
                if (windowSizeClass == WindowSizeClass.Compact) 12.dp else 24.dp,
            ),
    ) {
        if (windowSizeClass == WindowSizeClass.Expanded) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DateTimeCard(
                    garbageTypes = todayGarbageTypes,
                    garbageUpdateLabel = garbageUpdateLabel,
                    currentTime = currentTime,
                    currentYear = currentYear,
                    dateWithDay = dateWithDay,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                DailyFeedingCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    feedingLog = feedingLog,
                    petName = petName,
                    isLoading = feedingLoading,
                    error = feedingError,
                    actionError = feedingActionError,
                    onFeedClick = onFeedClick,
                    onRefresh = onRefreshFeeding,
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(if (windowSizeClass == WindowSizeClass.Compact) 0.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                DateTimeCard(
                    garbageTypes = todayGarbageTypes,
                    garbageUpdateLabel = garbageUpdateLabel,
                    currentTime = currentTime,
                    currentYear = currentYear,
                    dateWithDay = dateWithDay,
                    modifier = Modifier.fillMaxWidth(),
                )
                DailyFeedingCard(
                    modifier = Modifier.fillMaxWidth(),
                    feedingLog = feedingLog,
                    petName = petName,
                    isLoading = feedingLoading,
                    error = feedingError,
                    actionError = feedingActionError,
                    onFeedClick = onFeedClick,
                    onRefresh = onRefreshFeeding,
                )
            }
        }
    }
}

@Composable
fun DateTimeCard(
    garbageTypes: List<GarbageType>,
    garbageUpdateLabel: String,
    currentTime: String,
    currentYear: String,
    dateWithDay: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "$currentYear $dateWithDay",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.defaultMinSize(minHeight = CardHeaderMinHeight),
            )

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currentTime,
                    style = MaterialTheme.typography.displayExLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = garbageUpdateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (garbageTypes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "ゴミ回収なし",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (type in garbageTypes) {
                        Surface(
                            color = type.color.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null,
                                    tint = type.color,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = type.color,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyFeedingCard(
    feedingLog: FeedingLog,
    petName: String?,
    isLoading: Boolean,
    error: String?,
    actionError: String?,
    onFeedClick: (MealTime) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val doneCount = feedingLog.feedings.values.count { it.done }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderSection(
                    doneCount = doneCount,
                    petName = petName,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "更新",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            when {
                isLoading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "読み込みに失敗しました",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (mealTime in MealTime.displayOrder) {
                            val feeding = feedingLog.feedings[mealTime]
                            FeedingSection(
                                label = mealTime.label,
                                icon = mealTime.icon,
                                tint = mealTime.color,
                                isDone = feeding?.done == true,
                                time = feeding?.timestamp?.let { toJstHHMM(it.toJsString()).toString() },
                                onClick = { onFeedClick(mealTime) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (actionError != null) {
                        Text(
                            text = actionError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (feedingLog.note.isNotBlank()) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = feedingLog.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    doneCount: Int,
    petName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = CardHeaderMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = if (petName != null) "$petName のごはん" else "ごはん",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "毎日 5:00 更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 完了数に応じた色のバッジ
        Surface(
            color =
                if (doneCount == 3) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "$doneCount / 3",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color =
                    if (doneCount == 3) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
            )
        }
    }
}

@Composable
private fun FeedingSection(
    label: String,
    icon: ImageVector,
    tint: Color,
    isDone: Boolean,
    time: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(
                        color = tint.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isDone) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = FeedingDoneColor,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = time ?: "--:--",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("あげる", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
