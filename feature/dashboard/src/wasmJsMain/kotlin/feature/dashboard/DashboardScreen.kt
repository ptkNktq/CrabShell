@file:OptIn(ExperimentalWasmJsInterop::class)

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.theme.FeedingDoneColor
import core.ui.theme.color
import core.ui.theme.displayExLarge
import core.ui.theme.displayOrder
import core.ui.theme.icon
import core.ui.theme.label
import model.FeedingLog
import model.GarbageType
import model.MealTime

private val CardHeaderMinHeight = 48.dp

@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { DashboardViewModel(scope) }
    val windowSizeClass = LocalWindowSizeClass.current

    DashboardContent(
        loading = vm.uiState.isLoading,
        error = vm.uiState.error,
        feedingLog = vm.uiState.feedingLog,
        petName = vm.uiState.petName,
        todayGarbageTypes = vm.uiState.todayGarbageTypes,
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
    loading: Boolean,
    error: String?,
    feedingLog: FeedingLog,
    petName: String?,
    todayGarbageTypes: List<GarbageType>,
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
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Text("エラー: $error", color = MaterialTheme.colorScheme.error)
            }

            else -> {
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
                            currentTime = currentTime,
                            currentYear = currentYear,
                            dateWithDay = dateWithDay,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        DailyFeedingCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            feedingLog = feedingLog,
                            petName = petName,
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
                            currentTime = currentTime,
                            currentYear = currentYear,
                            dateWithDay = dateWithDay,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DailyFeedingCard(
                            modifier = Modifier.fillMaxWidth(),
                            feedingLog = feedingLog,
                            petName = petName,
                            onFeedClick = onFeedClick,
                            onRefresh = onRefreshFeeding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateTimeCard(
    garbageTypes: List<GarbageType>,
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
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = CardHeaderMinHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$currentYear $dateWithDay",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (garbageTypes.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        }
    }
}

@Composable
fun DailyFeedingCard(
    feedingLog: FeedingLog,
    petName: String?,
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
                text = "今日",
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
