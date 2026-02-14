package feature.money

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.User

@Composable
fun MoneyScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { MoneyViewModel(scope) }
    val isAdmin = (AuthStateHolder.state as? AuthState.Authenticated)?.user?.isAdmin == true
    val windowSizeClass = LocalWindowSizeClass.current

    MoneyContent(
        monthlyMoney = vm.monthlyMoney,
        currentMonth = vm.currentMonth,
        loading = vm.loading,
        saving = vm.saving,
        error = vm.error,
        users = vm.users,
        isAdmin = isAdmin,
        showDialog = vm.showDialog,
        editingItem = vm.editingItem,
        onPreviousMonth = { vm.goToPreviousMonth() },
        onNextMonth = { vm.goToNextMonth() },
        onAddItem = { vm.openAddDialog() },
        onEditItem = { vm.openEditDialog(it) },
        onDeleteItem = { vm.deleteItem(it) },
        onSaveItem = { name, amount, note, payments -> vm.saveItem(name, amount, note, payments) },
        onCloseDialog = { vm.closeDialog() },
        windowSizeClass = windowSizeClass,
    )
}

@Composable
internal fun MoneyContent(
    monthlyMoney: MonthlyMoney,
    currentMonth: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    users: List<User>,
    isAdmin: Boolean,
    showDialog: Boolean,
    editingItem: MoneyItem?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (MoneyItem) -> Unit,
    onDeleteItem: (MoneyItem) -> Unit,
    onSaveItem: (String, Long, String, List<Payment>) -> Unit,
    onCloseDialog: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 12.dp else 24.dp),
        ) {
            Text(
                text = "お金の管理",
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
                    ) {
                        SummaryCard(
                            items = monthlyMoney.items,
                            users = users,
                            isCompact = isCompact,
                        )

                        if (monthlyMoney.items.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "この月のデータはありません",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        for (item in monthlyMoney.items) {
                            MoneyItemCard(
                                item = item,
                                users = users,
                                isAdmin = isAdmin,
                                onEdit = { onEditItem(item) },
                                onDelete = { onDeleteItem(item) },
                                isCompact = isCompact,
                            )
                        }
                    }
                }
            }
        }

        // Admin FAB
        if (isAdmin && !loading && error == null) {
            FloatingActionButton(
                onClick = onAddItem,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (isCompact) 16.dp else 24.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }

        // 保存中インジケーター
        if (saving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    if (showDialog) {
        MoneyItemDialog(
            item = editingItem,
            users = users,
            onSave = onSaveItem,
            onDismiss = onCloseDialog,
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
private fun SummaryCard(
    items: List<MoneyItem>,
    users: List<User>,
    isCompact: Boolean,
) {
    val totalAmount = items.sumOf { it.amount }
    val userTotals = mutableMapOf<String, Long>()
    for (item in items) {
        for (payment in item.payments) {
            userTotals[payment.uid] = (userTotals[payment.uid] ?: 0L) + payment.amount
        }
    }

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
                text = "合計",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "¥${formatAmount(totalAmount)}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (userTotals.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                for ((uid, total) in userTotals) {
                    val userName = users.find { it.uid == uid }?.displayName ?: uid.take(8)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "¥${formatAmount(total)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyItemCard(
    item: MoneyItem,
    users: List<User>,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isCompact: Boolean,
) {
    val paymentTotal = item.payments.sumOf { it.amount }
    val mismatch = paymentTotal != item.amount && item.payments.isNotEmpty()

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
                    Text(
                        text = "¥${formatAmount(item.amount)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (isAdmin) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "編集",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "削除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.payments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                for (payment in item.payments) {
                    val userName = users.find { it.uid == payment.uid }?.displayName ?: payment.uid.take(8)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = userName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "¥${formatAmount(payment.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (mismatch) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "支払い合計 (¥${formatAmount(paymentTotal)}) が金額と一致しません",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyItemDialog(
    item: MoneyItem?,
    users: List<User>,
    onSave: (String, Long, String, List<Payment>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var amountText by remember(item) { mutableStateOf(item?.amount?.toString() ?: "") }
    var note by remember(item) { mutableStateOf(item?.note ?: "") }
    var paymentAmounts by remember(item) {
        mutableStateOf(
            users.associate { user ->
                user.uid to (item?.payments?.find { it.uid == user.uid }?.amount?.toString() ?: "")
            }
        )
    }

    val amount = amountText.toLongOrNull() ?: 0L
    val payments = paymentAmounts.mapNotNull { (uid, text) ->
        val a = text.toLongOrNull()
        if (a != null && a > 0) Payment(uid, a) else null
    }
    val paymentTotal = payments.sumOf { it.amount }
    val mismatch = payments.isNotEmpty() && paymentTotal != amount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item != null) "項目を編集" else "項目を追加") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("項目名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("金額 (円)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("備考") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3,
                )

                if (users.isNotEmpty()) {
                    Text(
                        text = "支払い分担",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    for (user in users) {
                        OutlinedTextField(
                            value = paymentAmounts[user.uid] ?: "",
                            onValueChange = { value ->
                                paymentAmounts = paymentAmounts.toMutableMap().apply {
                                    put(user.uid, value.filter { c -> c.isDigit() })
                                }
                            },
                            label = { Text(user.displayName ?: user.email) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            suffix = { Text("円") },
                        )
                    }

                    if (mismatch) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "支払い合計 ¥${formatAmount(paymentTotal)} / 金額 ¥${formatAmount(amount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, amount, note, payments) },
                enabled = name.isNotBlank() && amount > 0,
            ) {
                Text("保存")
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
