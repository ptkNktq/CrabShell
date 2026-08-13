package feature.payment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.ui.WindowSizeClass
import core.ui.components.AppButton
import core.ui.components.AppIconButton
import core.ui.components.AppOutlinedButton
import core.ui.extensions.displayName
import core.ui.extensions.icon
import core.ui.formatYen
import core.ui.util.dueDateGroupLabel
import core.ui.util.toJstMonthDay
import model.MoneyItem
import model.MonthlyMoney
import model.MonthlyMoneyStatus
import model.Payment
import model.User
import model.groupedByDueDate

@Composable
internal fun PaymentContent(
    monthlyMoney: MonthlyMoney,
    currentYearMonth: String,
    currentUid: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    isAdmin: Boolean = false,
    users: List<User> = emptyList(),
    isViewingOther: Boolean = false,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onConfirmPay: (Long) -> Unit,
    onDeletePayment: (String) -> Unit = {},
    deletingPaymentId: String? = null,
    onSwitchUser: (String) -> Unit = {},
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    // 自分の割当合計
    val totalAllocated =
        monthlyMoney.items.sumOf { item ->
            item.shares.filter { it.uid == currentUid }.sumOf { it.amount }
        }
    // 支払い済み合計
    val totalPaid = monthlyMoney.payments.sumOf { it.amount }
    val remaining = totalAllocated - totalPaid
    val status = monthlyMoney.status
    val frozen = status == MonthlyMoneyStatus.FROZEN

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCompact) {
            // Compact: 縦並び
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "支払い",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (isAdmin && users.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UserSwitcher(
                            users = users,
                            currentUid = currentUid,
                            onSwitchUser = onSwitchUser,
                            compact = true,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                MonthSelector(
                    yearMonth = currentYearMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    enabled = !saving && deletingPaymentId == null,
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
                    status = status,
                    saving = saving,
                    onDeletePayment = if (!frozen && !isViewingOther) onDeletePayment else null,
                    deletingPaymentId = deletingPaymentId,
                    onPayDueDate = if (!frozen && !isViewingOther) onConfirmPay else null,
                    modifier = Modifier.weight(1f),
                )
                if (!loading && error == null && !frozen && !isViewingOther) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PaymentInlineForm(
                        remaining = remaining,
                        saving = saving,
                        enabled = true,
                        onConfirmPay = onConfirmPay,
                    )
                }
            }
        } else {
            // Medium/Expanded: 左にリスト、右にフォーム
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "支払い",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (isAdmin && users.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        UserSwitcher(
                            users = users,
                            currentUid = currentUid,
                            onSwitchUser = onSwitchUser,
                            compact = false,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                MonthSelector(
                    yearMonth = currentYearMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    enabled = !saving && deletingPaymentId == null,
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
                        status = status,
                        saving = saving,
                        onDeletePayment = if (!frozen && !isViewingOther) onDeletePayment else null,
                        deletingPaymentId = deletingPaymentId,
                        onPayDueDate = if (!frozen && !isViewingOther) onConfirmPay else null,
                        modifier = Modifier.weight(1f),
                    )

                    if (!frozen && !isViewingOther) {
                        Spacer(modifier = Modifier.width(24.dp))

                        if (!loading && error == null) {
                            PaymentInlineForm(
                                remaining = remaining,
                                saving = saving,
                                enabled = true,
                                onConfirmPay = onConfirmPay,
                                modifier = Modifier.width(360.dp),
                            )
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
    status: MonthlyMoneyStatus,
    saving: Boolean = false,
    onDeletePayment: ((String) -> Unit)? = null,
    deletingPaymentId: String? = null,
    onPayDueDate: ((Long) -> Unit)? = null,
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
                        status = status,
                    )
                }

                // 支払い履歴
                if (monthlyMoney.payments.isNotEmpty()) {
                    item(key = "history-header") {
                        Text(
                            text = "支払い履歴",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(
                        monthlyMoney.payments.sortedByDescending { it.paidAt },
                        key = { it.id.ifEmpty { "${it.paidAt}-${it.amount}" } },
                    ) { payment ->
                        PaymentCard(
                            payment = payment,
                            isCompact = isCompact,
                            onDelete =
                                if (onDeletePayment != null && payment.id.isNotEmpty() && !payment.isRedemption) {
                                    { onDeletePayment(payment.id) }
                                } else {
                                    null
                                },
                            isDeleting = payment.id == deletingPaymentId,
                        )
                    }
                }

                // 項目内訳（支払期日ごとにグルーピング）
                if (monthlyMoney.items.isNotEmpty()) {
                    item(key = "items-header") {
                        Text(
                            text = "内訳",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    for ((dueDate, groupItems) in monthlyMoney.items.groupedByDueDate()) {
                        item(key = "due-header-${dueDate ?: "none"}") {
                            val groupTotal =
                                groupItems.sumOf { item ->
                                    item.shares.filter { it.uid == currentUid }.sumOf { it.amount }
                                }
                            DueDateGroupHeader(
                                dueDate = dueDate,
                                total = groupTotal,
                                enabled = !saving && deletingPaymentId == null,
                                onPay = if (onPayDueDate != null && groupTotal > 0) ({ onPayDueDate(groupTotal) }) else null,
                            )
                        }
                        items(groupItems, key = { it.id }) { item ->
                            ItemBreakdownCard(
                                item = item,
                                currentUid = currentUid,
                                isCompact = isCompact,
                            )
                        }
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

/**
 * 支払期日グループの見出し。合計額と、その額をそのまま支払い記録として登録するボタンを表示する。
 *
 * [onPay] は null（凍結中・他ユーザー閲覧中・合計額が0円のいずれか）の場合、合計額のテキスト表示のみになる。
 * ボタンを押すと [PaymentInlineForm] で金額入力して「記録」するのと同じ処理が、合計額を引数として即実行される。
 * 支払い記録は月次の合計にのみ加算され、どの支払期日グループ向けかという紐付けは持たない
 * （押した後もこのグループの表示自体は変化しない）ため、ボタンラベルに金額を明記して押す前に確認できるようにしている。
 */
@Composable
private fun DueDateGroupHeader(
    dueDate: String?,
    total: Long,
    enabled: Boolean,
    onPay: (() -> Unit)?,
) {
    val label = dueDateGroupLabel(dueDate)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onPay != null) {
            AppOutlinedButton(
                onClick = onPay,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${formatYen(total)} 支払う",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Text(
                text = formatYen(total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PaymentInlineForm(
    remaining: Long,
    saving: Boolean,
    enabled: Boolean,
    onConfirmPay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountText by remember(remaining) { mutableStateOf(if (remaining != 0L) remaining.toString() else "") }
    val amount = amountText.toLongOrNull() ?: 0L
    val inputEnabled = enabled && !saving

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
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

            if (enabled) {
                Text(
                    text = "残り ${formatYen(remaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                label = { Text("支払い額 (円)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = inputEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppButton(
                    onClick = { onConfirmPay(amount) },
                    enabled = amount > 0 && inputEnabled,
                ) {
                    Text("記録")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserSwitcher(
    users: List<User>,
    currentUid: String,
    onSwitchUser: (String) -> Unit,
    compact: Boolean,
) {
    val chipStyle =
        if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (user in users) {
            val selected = user.uid == currentUid
            FilterChip(
                selected = selected,
                onClick = { if (!selected) onSwitchUser(user.uid) },
                label = {
                    Text(
                        text = user.displayName ?: user.uid.take(8),
                        style = chipStyle,
                    )
                },
                leadingIcon =
                    if (selected) {
                        {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun MonthSelector(
    yearMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean = true,
) {
    val parts = yearMonth.split("-")
    val displayText = "${parts[0]}年${parts[1].toInt()}月"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIconButton(onClick = onPrevious, enabled = enabled, debounceMillis = 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前月")
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleLarge,
        )
        AppIconButton(onClick = onNext, enabled = enabled, debounceMillis = 0) {
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
    status: MonthlyMoneyStatus,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().let {
                if (!isCompact) it.widthIn(max = 600.dp) else it
            },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "今月の支払い",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonthlyMoneyStatusBadge(status = status)
                    if (remaining <= 0 && totalAllocated > 0) {
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
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("合計", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatYen(totalAllocated),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("支払済", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatYen(totalPaid),
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
                    formatYen(remaining),
                    style = MaterialTheme.typography.headlineMedium,
                    color =
                        if (remaining <= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                )
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: Payment,
    isCompact: Boolean,
    onDelete: (() -> Unit)? = null,
    isDeleting: Boolean = false,
) {
    val hasNote = payment.note.isNotEmpty()

    Card(
        modifier =
            Modifier.fillMaxWidth().let {
                if (!isCompact) it.widthIn(max = 600.dp) else it
            },
        colors =
            CardDefaults.cardColors(
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
                    text = toJstMonthDay(payment.paidAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasNote) {
                    Text(
                        text = payment.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatYen(payment.amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasNote) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp).padding(horizontal = 7.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (onDelete != null) {
                    AppIconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "支払いを取り消す",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemBreakdownCard(
    item: MoneyItem,
    currentUid: String,
    isCompact: Boolean,
) {
    val myAllocation = item.shares.filter { it.uid == currentUid }.sumOf { it.amount }
    if (myAllocation == 0L) return

    Card(
        modifier =
            Modifier.fillMaxWidth().let {
                if (!isCompact) it.widthIn(max = 600.dp) else it
            },
        colors =
            CardDefaults.cardColors(
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
                text = formatYen(myAllocation),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MonthlyMoneyStatusBadge(status: MonthlyMoneyStatus) {
    val (container, onContainer) =
        when (status) {
            MonthlyMoneyStatus.PENDING ->
                MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            MonthlyMoneyStatus.CONFIRMED ->
                MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            MonthlyMoneyStatus.FROZEN ->
                MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
        }
    Surface(color = container, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                status.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = onContainer,
            )
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
            )
        }
    }
}
