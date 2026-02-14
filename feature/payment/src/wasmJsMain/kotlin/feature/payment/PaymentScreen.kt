package feature.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import model.MoneyItem
import model.MonthlyMoney
import model.PaymentRecord

@Composable
fun PaymentScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { PaymentViewModel(scope) }
    val windowSizeClass = LocalWindowSizeClass.current

    PaymentContent(
        monthlyMoney = vm.monthlyMoney,
        currentMonth = vm.currentMonth,
        currentUid = vm.currentUid,
        loading = vm.loading,
        saving = vm.saving,
        error = vm.error,
        onPreviousMonth = { vm.goToPreviousMonth() },
        onNextMonth = { vm.goToNextMonth() },
        onConfirmPay = { amount -> vm.recordPayment(amount) },
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun PaymentContent(
    monthlyMoney: MonthlyMoney,
    currentMonth: String,
    currentUid: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onConfirmPay: (Long) -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    // 自分の割当合計
    val totalAllocated = monthlyMoney.items.sumOf { item ->
        item.payments.filter { it.uid == currentUid }.sumOf { it.amount }
    }
    // 支払い済み合計
    val totalPaid = monthlyMoney.paymentRecords.sumOf { it.amount }
    val remaining = totalAllocated - totalPaid

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCompact) {
            // Compact: 縦並び
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                Text(
                    text = "お支払い",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                MonthSelector(
                    month = currentMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaymentListContent(
                    monthlyMoney = monthlyMoney,
                    currentUid = currentUid,
                    totalAllocated = totalAllocated,
                    totalPaid = totalPaid,
                    remaining = remaining,
                    loading = loading,
                    error = error,
                    isCompact = true,
                    modifier = Modifier.weight(1f),
                )
                if (!loading && error == null && remaining > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PaymentInlineForm(
                        remaining = remaining,
                        saving = saving,
                        onConfirmPay = onConfirmPay,
                    )
                }
            }
        } else {
            // Medium/Expanded: 左にリスト、右にフォーム
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = "お支払い",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                MonthSelector(
                    month = currentMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    PaymentListContent(
                        monthlyMoney = monthlyMoney,
                        currentUid = currentUid,
                        totalAllocated = totalAllocated,
                        totalPaid = totalPaid,
                        remaining = remaining,
                        loading = loading,
                        error = error,
                        isCompact = false,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    if (!loading && error == null && remaining > 0) {
                        PaymentInlineForm(
                            remaining = remaining,
                            saving = saving,
                            onConfirmPay = onConfirmPay,
                            modifier = Modifier.width(360.dp),
                        )
                    } else if (!loading && error == null) {
                        Card(
                            modifier = Modifier.width(360.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "支払い完了",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (saving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun PaymentListContent(
    monthlyMoney: MonthlyMoney,
    currentUid: String,
    totalAllocated: Long,
    totalPaid: Long,
    remaining: Long,
    loading: Boolean,
    error: String?,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            Box(modifier = modifier) {
                Text("エラー: $error", color = MaterialTheme.colorScheme.error)
            }
        }

        else -> {
            val spacing = if (isCompact) 8.dp else 12.dp
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // サマリーカード
                item(key = "summary") {
                    SummaryCard(
                        totalAllocated = totalAllocated,
                        totalPaid = totalPaid,
                        remaining = remaining,
                        isCompact = isCompact,
                    )
                }

                // 支払い履歴
                if (monthlyMoney.paymentRecords.isNotEmpty()) {
                    item(key = "history-header") {
                        Text(
                            text = "支払い履歴",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(monthlyMoney.paymentRecords.reversed(), key = { "${it.paidAt}-${it.amount}" }) { record ->
                        PaymentRecordCard(record = record, isCompact = isCompact)
                    }
                }

                // 項目内訳
                if (monthlyMoney.items.isNotEmpty()) {
                    item(key = "items-header") {
                        Text(
                            text = "内訳",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(monthlyMoney.items, key = { it.id }) { item ->
                        ItemBreakdownCard(
                            item = item,
                            currentUid = currentUid,
                            isCompact = isCompact,
                        )
                    }
                }

                if (monthlyMoney.items.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "今月の割当はありません",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentInlineForm(
    remaining: Long,
    saving: Boolean,
    onConfirmPay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountText by remember(remaining) { mutableStateOf(remaining.toString()) }
    val amount = amountText.toLongOrNull() ?: 0L

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "支払いを記録",
                style = MaterialTheme.typography.titleLarge,
            )

            Text(
                text = "残り ¥${formatAmount(remaining)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                label = { Text("支払い額 (円)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onConfirmPay(amount) },
                    enabled = amount > 0 && !saving,
                ) {
                    Text("記録")
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    month: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val parts = month.split("-")
    val displayText = "${parts[0]}年${parts[1].toInt()}月"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前月")
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "翌月")
        }
    }
}

@Composable
private fun SummaryCard(
    totalAllocated: Long,
    totalPaid: Long,
    remaining: Long,
    isCompact: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().let {
            if (!isCompact) it.widthIn(max = 600.dp) else it
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "今月のお支払い",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("合計", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "¥${formatAmount(totalAllocated)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("支払済", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "¥${formatAmount(totalPaid)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("残り", style = MaterialTheme.typography.titleMedium)
                Text(
                    "¥${formatAmount(remaining)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (remaining <= 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PaymentRecordCard(
    record: PaymentRecord,
    isCompact: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().let {
            if (!isCompact) it.widthIn(max = 600.dp) else it
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDate(record.paidAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "¥${formatAmount(record.amount)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ItemBreakdownCard(
    item: MoneyItem,
    currentUid: String,
    isCompact: Boolean,
) {
    val myAllocation = item.payments.filter { it.uid == currentUid }.sumOf { it.amount }
    if (myAllocation <= 0) return

    Card(
        modifier = Modifier.fillMaxWidth().let {
            if (!isCompact) it.widthIn(max = 600.dp) else it
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (item.note.isNotBlank()) {
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "¥${formatAmount(myAllocation)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatAmount(amount: Long): String {
    val str = amount.toString()
    val result = StringBuilder()
    for ((i, c) in str.reversed().withIndex()) {
        if (i > 0 && i % 3 == 0) result.append(',')
        result.append(c)
    }
    return result.reverse().toString()
}

private fun formatDate(isoString: String): String {
    return try {
        val date = isoString.substringBefore("T")
        val time = isoString.substringAfter("T").substringBefore(".")
        val parts = date.split("-")
        val timeParts = time.split(":")
        "${parts[1].toInt()}/${parts[2].toInt()} ${timeParts[0]}:${timeParts[1]}"
    } catch (_: Exception) {
        isoString
    }
}
