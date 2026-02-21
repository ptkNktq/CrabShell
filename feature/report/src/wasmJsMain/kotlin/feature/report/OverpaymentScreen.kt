package feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import feature.report.components.UserBalanceCard
import model.UserBalance
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OverpaymentScreen(vm: OverpaymentViewModel = koinViewModel()) {
    val isCompact = LocalWindowSizeClass.current == WindowSizeClass.Compact

    OverpaymentContent(
        balances = vm.uiState.balances,
        period = vm.uiState.period,
        isLoading = vm.uiState.isLoading,
        onRefresh = vm::loadBalances,
        isCompact = isCompact,
        redemptionForm = vm.uiState.redemptionForm,
        onSelectUser = vm::onSelectUser,
        onAmountChange = vm::onRedemptionAmountChange,
        onMonthPrevious = vm::onRedemptionMonthPrevious,
        onMonthNext = vm::onRedemptionMonthNext,
        onFillRemaining = vm::onFillRemainingAmount,
        onClear = vm::onClearForm,
        onConfirm = vm::onConfirmRedemption,
    )
}

@Composable
internal fun OverpaymentContent(
    balances: List<UserBalance>,
    period: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    isCompact: Boolean = false,
    redemptionForm: RedemptionFormState,
    onSelectUser: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onFillRemaining: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
) {
    val outerPadding = if (isCompact) 12.dp else 24.dp

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(outerPadding)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "過払い額",
            style = if (isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

        UserBalanceCard(
            balances = balances,
            period = period,
            isLoading = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.widthIn(max = 600.dp),
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

        RedemptionInlineCard(
            balances = balances,
            form = redemptionForm,
            onSelectUser = onSelectUser,
            onAmountChange = onAmountChange,
            onMonthPrevious = onMonthPrevious,
            onMonthNext = onMonthNext,
            onFillRemaining = onFillRemaining,
            onClear = onClear,
            onConfirm = onConfirm,
            modifier = Modifier.widthIn(max = 600.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedemptionInlineCard(
    balances: List<UserBalance>,
    form: RedemptionFormState,
    onSelectUser: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onFillRemaining: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputEnabled = !form.isSaving

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
                text = "過払い金から支払い",
                style = MaterialTheme.typography.titleLarge,
            )

            if (form.isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ユーザー選択 FilterChips
            if (balances.isNotEmpty()) {
                Text(
                    text = "対象ユーザー",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (balance in balances) {
                        val selected = balance.uid == form.selectedUid
                        FilterChip(
                            selected = selected,
                            onClick = { if (!selected) onSelectUser(balance.uid) },
                            label = { Text(balance.displayName, style = MaterialTheme.typography.labelMedium) },
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
                            enabled = inputEnabled,
                        )
                    }
                }
            }

            // 月セレクター
            Text(
                text = "記録先の月",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RedemptionMonthSelector(
                month = form.selectedMonth,
                onPrevious = onMonthPrevious,
                onNext = onMonthNext,
                enabled = inputEnabled,
            )

            // 金額入力
            OutlinedTextField(
                value = form.amountText,
                onValueChange = { onAmountChange(it.filter { c -> c.isDigit() }) },
                label = { Text("金額 (円)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = inputEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    if (form.selectedUid.isNotEmpty()) {
                        TextButton(
                            onClick = onFillRemaining,
                            enabled = inputEnabled,
                        ) {
                            Text("残額全額")
                        }
                    }
                },
            )

            // エラー表示
            if (form.error != null) {
                Text(
                    text = form.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // アクションボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onClear,
                    enabled = inputEnabled,
                ) {
                    Text("クリア")
                }
                Button(
                    onClick = onConfirm,
                    enabled = form.selectedUid.isNotEmpty() && form.amount > 0L && inputEnabled,
                ) {
                    Text("記録")
                }
            }
        }
    }
}

@Composable
private fun RedemptionMonthSelector(
    month: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    enabled: Boolean = true,
) {
    val parts = month.split("-")
    val displayText = "${parts[0]}年${parts[1].toInt()}月"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前月")
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "翌月")
        }
    }
}
