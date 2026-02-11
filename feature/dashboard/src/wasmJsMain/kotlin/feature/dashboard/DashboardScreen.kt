@file:OptIn(ExperimentalWasmJsInterop::class)

package feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.FeedingLog
import model.MealTime

@Composable
fun DashboardScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { DashboardViewModel(scope) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        when {
            vm.loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            vm.error != null -> {
                Text("Error: ${vm.error}", color = MaterialTheme.colorScheme.error)
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // TODO: 今後もう半分には別のコンテンツを表示したいのでダミーで追加しておく
                    Box(modifier = Modifier.weight(1f))
                    DailyFeedingCard(
                        modifier = Modifier.weight(1f),
                        feedingLog = vm.feedingLog,
                        onFeedClick = { vm.feed(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun DailyFeedingCard(
    feedingLog: FeedingLog,
    onFeedClick: (MealTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val doneCount = feedingLog.feedings.values.count { it.done }

            HeaderSection(doneCount = doneCount)

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val meals = listOf(
                    Triple(MealTime.MORNING, Icons.Default.WbTwilight, Color(0xCDFF4E4E)),
                    Triple(MealTime.LUNCH, Icons.Default.WbSunny, Color(0xFFFBC02D)),
                    Triple(MealTime.EVENING, Icons.Default.Bedtime, Color(0xFF5C6BC0)),
                )
                val labels = listOf("Morning", "Lunch", "Evening")
                for ((index, triple) in meals.withIndex()) {
                    val (mealTime, icon, tint) = triple
                    val feeding = feedingLog.feedings[mealTime]
                    FeedingSection(
                        label = labels[index],
                        icon = icon,
                        tint = tint,
                        isDone = feeding?.done == true,
                        time = feeding?.timestamp?.let { toJstHHMM(it.toJsString()).toString() },
                        onClick = { onFeedClick(mealTime) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    doneCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Feeding",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 完了数に応じた色のバッジ
        Surface(
            color = if (doneCount == 3) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "$doneCount / 3",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (doneCount == 3) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = tint.copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isDone) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = time ?: "--:--",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Feed", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
