@file:OptIn(ExperimentalWasmJsInterop::class)

package core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.ui.util.firstDayOfWeekJs
import core.ui.util.daysInMonthJs

private val MONTH_NAMES = arrayOf(
    "1月", "2月", "3月", "4月", "5月", "6月",
    "7月", "8月", "9月", "10月", "11月", "12月",
)

private val DAY_OF_WEEK_LABELS = arrayOf("日", "月", "火", "水", "木", "金", "土")

@Composable
fun CalendarView(
    selectedDate: String,
    today: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Parse selectedDate to get initial year/month
    val selectedYear = selectedDate.substring(0, 4).toInt()
    val selectedMonth = selectedDate.substring(5, 7).toInt()

    var displayYear by remember { mutableStateOf(selectedYear) }
    var displayMonth by remember { mutableStateOf(selectedMonth) }

    // Sync display month when selectedDate changes externally
    LaunchedEffect(selectedDate) {
        displayYear = selectedDate.substring(0, 4).toInt()
        displayMonth = selectedDate.substring(5, 7).toInt()
    }

    val todayYear = today.substring(0, 4).toInt()
    val todayMonth = today.substring(5, 7).toInt()

    Column(modifier = modifier) {
        MonthHeader(
            year = displayYear,
            month = displayMonth,
            onPreviousMonth = {
                if (displayMonth == 1) {
                    displayYear--
                    displayMonth = 12
                } else {
                    displayMonth--
                }
            },
            onNextMonth = {
                if (displayMonth == 12) {
                    displayYear++
                    displayMonth = 1
                } else {
                    displayMonth++
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        DayOfWeekHeaders()

        Spacer(modifier = Modifier.height(4.dp))

        DayGrid(
            year = displayYear,
            month = displayMonth,
            selectedDate = selectedDate,
            today = today,
            onDateSelected = onDateSelected,
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                displayYear = todayYear
                displayMonth = todayMonth
                onDateSelected(today)
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("今日")
        }
    }
}

@Composable
private fun MonthHeader(
    year: Int,
    month: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前月")
        }
        Text(
            text = "${year}年 ${MONTH_NAMES[month - 1]}",
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "翌月")
        }
    }
}

@Composable
private fun DayOfWeekHeaders() {
    Row(modifier = Modifier.fillMaxWidth()) {
        for (label in DAY_OF_WEEK_LABELS) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayGrid(
    year: Int,
    month: Int,
    selectedDate: String,
    today: String,
    onDateSelected: (String) -> Unit,
) {
    val firstDow = firstDayOfWeekJs(year, month)
    val daysInMonth = daysInMonthJs(year, month)

    // 6 rows x 7 columns fixed grid
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var dayCounter = 1
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val cellIndex = week * 7 + dow
                    if (cellIndex < firstDow || dayCounter > daysInMonth) {
                        // Empty cell
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = dayCounter
                        val dateStr = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                        DayCell(
                            day = day,
                            isSelected = dateStr == selectedDate,
                            isToday = dateStr == today,
                            onClick = { onDateSelected(dateStr) },
                            modifier = Modifier.weight(1f),
                        )
                        dayCounter++
                    }
                }
            }
            // Stop rendering rows after all days are placed
            if (dayCounter > daysInMonth) break
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(primary, CircleShape)
                    isToday -> Modifier.border(1.5.dp, primary, CircleShape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) onPrimary else onSurface,
        )
    }
}
