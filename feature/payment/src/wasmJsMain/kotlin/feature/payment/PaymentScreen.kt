package feature.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payment
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
        error = vm.error,
        payingItem = vm.payingItem,
        onPreviousMonth = { vm.goToPreviousMonth() },
        onNextMonth = { vm.goToNextMonth() },
        onPayItem = { vm.openPayDialog(it) },
        onConfirmPay = { item, amount -> vm.recordPayment(item, amount) },
        onClosePayDialog = { vm.closePayDialog() },
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun PaymentContent(
    monthlyMoney: MonthlyMoney,
    currentMonth: String,
    currentUid: String,
    loading: Boolean,
    error: String?,
    payingItem: MoneyItem?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPayItem: (MoneyItem) -> Unit,
    onConfirmPay: (MoneyItem, Long) -> Unit,
    onClosePayDialog: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 12.dp else 24.dp),
    ) {
        Text(
            text = "お支払い",
            style = if (isCompact) MaterialTheme.typography.headlineSmall
            else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        MonthSelector(
            month = currentMonth,
            onPrevious = onPreviousMonth,
            onNext = onNextMonth,
        )

        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                Text("エラー: $error", color = MaterialTheme.colorScheme.error)
            }

            else -> {
                val spacing = if (isCompact) 8.dp else 12.dp

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    item(key = "summary") {
                        PaymentSummaryCard(
                            items = monthlyMoney.items,
                            currentUid = currentUid,
                            isCompact = isCompact,
                        )
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

                    items(monthlyMoney.items, key = { it.id }) { item ->
                        PaymentItemCard(
                            item = item,
                            currentUid = currentUid,
                            onPay = { onPayItem(item) },
                            isCompact = isCompact,
                        )
                    }
                }
            }
        }
    }

    if (payingItem != null) {
        PayDialog(
            item = payingItem,
            currentUid = currentUid,
            onConfirm = { amount -> onConfirmPay(payingItem, amount) },
            onDismiss = onClosePayDialog,
        )
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
private fun PaymentSummaryCard(
    items: List<MoneyItem>,
    currentUid: String,
    isCompact: Boolean,
) {
    var totalAllocated = 0L
    var totalPaid = 0L

    for (item in items) {
        for (payment in item.payments) {
            if (payment.uid == currentUid) {
                totalAllocated += payment.amount
                totalPaid += payment.records.sumOf { it.amount }
            }
        }
    }

    val remaining = totalAllocated - totalPaid

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
            Spacer(modifier = Modifier.height(8.dp))

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
                Text(
                    "残り",
                    style = MaterialTheme.typography.titleMedium,
                )
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
private fun PaymentItemCard(
    item: MoneyItem,
    currentUid: String,
    onPay: () -> Unit,
    isCompact: Boolean,
) {
    val myPayment = item.payments.find { it.uid == currentUid } ?: return
    val allocated = myPayment.amount
    val paid = myPayment.records.sumOf { it.amount }
    val remaining = allocated - paid
    val isFullyPaid = remaining <= 0

    Card(
        modifier = Modifier.fillMaxWidth().let {
            if (!isCompact) it.widthIn(max = 600.dp) else it
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (item.note.isNotBlank()) {
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isFullyPaid) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "支払済",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    FilledTonalButton(onClick = onPay) {
                        Icon(
                            Icons.Default.Payment,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("支払い")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("割当額", style = MaterialTheme.typography.bodyMedium)
                Text("¥${formatAmount(allocated)}", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("支払済", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "¥${formatAmount(paid)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (!isFullyPaid) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "残り",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "¥${formatAmount(remaining)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 支払い履歴
            if (myPayment.records.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "支払い履歴",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                for (record in myPayment.records) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDate(record.paidAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "¥${formatAmount(record.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayDialog(
    item: MoneyItem,
    currentUid: String,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val myPayment = item.payments.find { it.uid == currentUid }
    val remaining = if (myPayment != null) {
        myPayment.amount - myPayment.records.sumOf { it.amount }
    } else {
        0L
    }

    var amountText by remember(item) { mutableStateOf(remaining.coerceAtLeast(0).toString()) }
    val amount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("支払いを記録") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "残り ¥${formatAmount(remaining.coerceAtLeast(0))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("支払い額 (円)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amount) },
                enabled = amount > 0,
            ) {
                Text("記録")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
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
    // "2026-02-14T12:34:56.789Z" → "2/14 12:34"
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
