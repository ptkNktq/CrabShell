package feature.money

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import model.MoneyItem
import model.MonthlyMoney
import model.Payment
import model.PaymentRecord
import model.User

@Composable
fun MoneyScreen() {
    val scope = rememberCoroutineScope()
    val vm = remember { MoneyViewModel(scope) }
    val windowSizeClass = LocalWindowSizeClass.current

    MoneyContent(
        monthlyMoney = vm.monthlyMoney,
        currentMonth = vm.currentMonth,
        loading = vm.loading,
        saving = vm.saving,
        error = vm.error,
        users = vm.users,
        editingItem = vm.editingItem,
        formKey = vm.formKey,
        onPreviousMonth = { vm.goToPreviousMonth() },
        onNextMonth = { vm.goToNextMonth() },
        onEditItem = { vm.editItem(it) },
        onClearForm = { vm.clearForm() },
        onDeleteItem = { vm.deleteItem(it) },
        onSaveItem = { name, amount, note, payments, recurring -> vm.saveItem(name, amount, note, payments, recurring) },
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
    editingItem: MoneyItem?,
    formKey: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditItem: (MoneyItem) -> Unit,
    onClearForm: () -> Unit,
    onDeleteItem: (MoneyItem) -> Unit,
    onSaveItem: (String, Long, String, List<Payment>, Boolean) -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact
    // Compact 用: フォーム表示切替
    var showFormCompact by remember { mutableStateOf(false) }

    // editingItem がクリアされたらフォームを閉じる（保存成功時）
    LaunchedEffect(editingItem) {
        if (editingItem == null) showFormCompact = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCompact) {
            if (showFormCompact) {
                // Compact: フォーム表示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                ) {
                    Text(
                        text = if (editingItem != null) "項目を編集" else "項目を追加",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MoneyItemForm(
                        item = editingItem,
                        formKey = formKey,
                        users = users,
                        saving = saving,
                        onSave = onSaveItem,
                        onCancel = {
                            onClearForm()
                            showFormCompact = false
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            } else {
                // Compact: リスト表示
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                ) {
                    Text(
                        text = "お金の管理",
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

                    // 追加ボタン
                    if (!loading && error == null) {
                        Button(
                            onClick = {
                                onClearForm()
                                showFormCompact = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("項目を追加")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    MoneyListContent(
                        monthlyMoney = monthlyMoney,
                        loading = loading,
                        error = error,
                        users = users,
                        isCompact = true,
                        onEditItem = { item ->
                            onEditItem(item)
                            showFormCompact = true
                        },
                        onDeleteItem = onDeleteItem,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            // Medium/Expanded: 左にリスト、右にフォーム（常時表示）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(
                    text = "お金の管理",
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
                    MoneyListContent(
                        monthlyMoney = monthlyMoney,
                        loading = loading,
                        error = error,
                        users = users,
                        isCompact = false,
                        onEditItem = onEditItem,
                        onDeleteItem = onDeleteItem,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    MoneyItemForm(
                        item = editingItem,
                        formKey = formKey,
                        users = users,
                        saving = saving,
                        onSave = onSaveItem,
                        onCancel = onClearForm,
                        modifier = Modifier.width(400.dp),
                    )
                }
            }
        }

        // 保存中インジケーター
        if (saving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

}

@Composable
private fun MoneyListContent(
    monthlyMoney: MonthlyMoney,
    loading: Boolean,
    error: String?,
    users: List<User>,
    isCompact: Boolean,
    onEditItem: (MoneyItem) -> Unit,
    onDeleteItem: (MoneyItem) -> Unit,
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
                item(key = "summary") {
                    SummaryCard(
                        items = monthlyMoney.items,
                        users = users,
                        paymentRecords = monthlyMoney.paymentRecords,
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
                                text = "この月のデータはありません",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val sortedItems = monthlyMoney.items.sortedByDescending { it.recurring }
                items(sortedItems, key = { it.id }) { item ->
                    MoneyItemCard(
                        item = item,
                        users = users,
                        onEdit = { onEditItem(item) },
                        onDelete = { onDeleteItem(item) },
                        isCompact = isCompact,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoneyItemForm(
    item: MoneyItem?,
    formKey: Int,
    users: List<User>,
    saving: Boolean,
    onSave: (String, Long, String, List<Payment>, Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // formKey をキーに含めて、clearForm 時に確実にリセットする
    val key = item ?: formKey
    var name by remember(key) { mutableStateOf(item?.name ?: "") }
    var amountText by remember(key) { mutableStateOf(item?.amount?.toString() ?: "") }
    var note by remember(key) { mutableStateOf(item?.note ?: "") }
    var recurring by remember(key) { mutableStateOf(item?.recurring ?: false) }
    var paymentAmounts by remember(key) {
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
    val isEditing = item != null

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isEditing) "項目を編集" else "項目を追加",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("項目名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !saving,
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("金額 (円)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !saving,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("備考") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                enabled = !saving,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "毎月繰り返し",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = recurring,
                    onCheckedChange = { recurring = it },
                    enabled = !saving,
                )
            }

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
                                put(user.uid, value.filter { c -> c.isDigit() || c == '-' })
                            }
                        },
                        label = { Text(user.displayName ?: user.uid) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("円") },
                        enabled = !saving,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditing) {
                    TextButton(onClick = onCancel, enabled = !saving) {
                        Text("キャンセル")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = { onSave(name, amount, note, payments, recurring) },
                    enabled = name.isNotBlank() && amount != 0L && !saving,
                ) {
                    Text(if (isEditing) "保存" else "追加")
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
    items: List<MoneyItem>,
    users: List<User>,
    paymentRecords: List<PaymentRecord>,
    isCompact: Boolean,
) {
    val totalAmount = items.sumOf { it.amount }

    // ユーザーごとの割当合計
    val userAllocated = mutableMapOf<String, Long>()
    for (item in items) {
        for (payment in item.payments) {
            userAllocated[payment.uid] = (userAllocated[payment.uid] ?: 0L) + payment.amount
        }
    }

    // ユーザーごとの支払い済み合計
    val userPaid = mutableMapOf<String, Long>()
    for (record in paymentRecords) {
        userPaid[record.uid] = (userPaid[record.uid] ?: 0L) + record.amount
    }

    // 全ユーザーが割当を満たしているかチェック
    val allPaid = userAllocated.isNotEmpty() && userAllocated.all { (uid, allocated) ->
        (userPaid[uid] ?: 0L) >= allocated
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "合計",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "¥${formatAmount(totalAmount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (allPaid) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "支払い完了",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            if (userAllocated.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                for ((uid, allocated) in userAllocated) {
                    val userName = users.find { it.uid == uid }?.displayName ?: uid.take(8)
                    val paid = userPaid[uid] ?: 0L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "¥${formatAmount(paid)} / ¥${formatAmount(allocated)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (paid >= allocated) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (item.recurring) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = "毎月",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                    Text(
                        text = "¥${formatAmount(item.amount)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

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

private fun formatAmount(amount: Long): String {
    val str = amount.toString()
    val result = StringBuilder()
    for ((i, c) in str.reversed().withIndex()) {
        if (i > 0 && i % 3 == 0) result.append(',')
        result.append(c)
    }
    return result.reverse().toString()
}
