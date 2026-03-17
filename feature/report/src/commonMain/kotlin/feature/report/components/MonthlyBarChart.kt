package feature.report.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import model.MonthlyExpenseSummary

private val HorizontalPadding = 16.dp
private val BarSpacing = 12.dp

@Composable
fun MonthlyBarChart(
    months: List<MonthlyExpenseSummary>,
    selectedMonth: String,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onSurfaceVariantColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onMonthClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            color = onSurfaceVariantColor,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            textAlign = TextAlign.Center,
        )
    val amountStyle =
        TextStyle(
            color = onSurfaceVariantColor,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            textAlign = TextAlign.Center,
        )

    val currentOnMonthClick by rememberUpdatedState(onMonthClick)

    val density = LocalDensity.current
    val horizontalPaddingPx = remember(density) { with(density) { HorizontalPadding.toPx() } }
    val barSpacingPx = remember(density) { with(density) { BarSpacing.toPx() } }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(months) {
                    if (months.isEmpty()) return@pointerInput
                    detectTapGestures { offset ->
                        val chartWidth = size.width - horizontalPaddingPx * 2
                        val barWidth =
                            (chartWidth - barSpacingPx * (months.size - 1)) / months.size
                        months.forEachIndexed { index, summary ->
                            val x = horizontalPaddingPx + index * (barWidth + barSpacingPx)
                            if (offset.x in x..(x + barWidth)) {
                                currentOnMonthClick(summary.month)
                                return@detectTapGestures
                            }
                        }
                    }
                },
    ) {
        if (months.isEmpty()) return@Canvas

        val maxAmount = months.maxOf { it.totalAmount }.coerceAtLeast(1L)
        val barCount = months.size
        val chartWidth = size.width - horizontalPaddingPx * 2
        val barWidth = (chartWidth - barSpacingPx * (barCount - 1)) / barCount
        val topPadding = 28.dp.toPx()
        val bottomPadding = 24.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding
        val cornerRadius = 4.dp.toPx()

        months.forEachIndexed { index, summary ->
            val barHeight =
                if (maxAmount > 0) {
                    (summary.totalAmount.toFloat() / maxAmount) * chartHeight
                } else {
                    0f
                }
            val x = horizontalPaddingPx + index * (barWidth + barSpacingPx)
            val y = topPadding + chartHeight - barHeight

            val isSelected = summary.month == selectedMonth
            val barColor = if (isSelected) primaryColor else primaryColor.copy(alpha = 0.4f)

            // 棒グラフ
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius =
                    androidx.compose.ui.geometry
                        .CornerRadius(cornerRadius, cornerRadius),
            )

            // 金額ラベル（棒の上）
            if (summary.totalAmount > 0) {
                val amountText = formatChartAmount(summary.totalAmount)
                val amountLayout =
                    textMeasurer.measure(
                        text = amountText,
                        style = amountStyle,
                    )
                drawText(
                    textLayoutResult = amountLayout,
                    topLeft =
                        Offset(
                            x + (barWidth - amountLayout.size.width) / 2,
                            y - amountLayout.size.height - 4.dp.toPx(),
                        ),
                )
            }

            // 月名ラベル（下部）
            val monthLabel = formatMonthLabel(summary.month)
            val monthLayout =
                textMeasurer.measure(
                    text = monthLabel,
                    style = labelStyle,
                )
            drawText(
                textLayoutResult = monthLayout,
                topLeft =
                    Offset(
                        x + (barWidth - monthLayout.size.width) / 2,
                        topPadding + chartHeight + 4.dp.toPx(),
                    ),
            )
        }
    }
}

private fun formatChartAmount(amount: Long): String =
    if (amount >= 10000) {
        "¥${amount / 10000}万"
    } else {
        "¥$amount"
    }

private fun formatMonthLabel(month: String): String {
    val parts = month.split("-")
    return if (parts.size == 2) "${parts[1].toInt()}月" else month
}
