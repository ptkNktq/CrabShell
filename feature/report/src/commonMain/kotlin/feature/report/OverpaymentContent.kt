package feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import core.ui.components.AppButton
import core.ui.components.AppIconButton
import core.ui.components.AppOutlinedButton
import core.ui.components.AppTextButton
import feature.report.components.UserBalanceCard
import model.UserBalance

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
    onNoteChange: (String) -> Unit,
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
            onNoteChange = onNoteChange,
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
    onNoteChange: (String) -> Unit,
    onMonthPrevious: () -> Unit,
    onMonthNext: () -> Unit,
    onFillRemaining: () -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputEnabled = !form.isSaving
    val frozen = form.isMonthFrozen

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
                yearMonth = form.selectedYearMonth,
                onPrevious = onMonthPrevious,
                onNext = onMonthNext,
                enabled = inputEnabled,
            )

            // 凍結警告
            if (frozen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "この月は凍結されています",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // 金額入力 + 残額全額ボタン
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = form.amountText,
                    onValueChange = { onAmountChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("金額 (円)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = inputEnabled && !frozen,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AppOutlinedButton(
                    onClick = onFillRemaining,
                    enabled = inputEnabled && !frozen && form.selectedUid.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("残額全額", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 備考
            OutlinedTextField(
                value = form.noteText,
                onValueChange = onNoteChange,
                label = { Text("備考") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = inputEnabled && !frozen,
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
                AppTextButton(
                    onClick = onClear,
                    enabled = inputEnabled,
                ) {
                    Text("クリア")
                }
                Spacer(modifier = Modifier.width(8.dp))
                AppButton(
                    onClick = onConfirm,
                    enabled = form.canSubmit,
                ) {
                    Text("記録")
                }
            }
        }
    }
}

@Composable
private fun RedemptionMonthSelector(
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
            style = MaterialTheme.typography.titleMedium,
        )
        AppIconButton(onClick = onNext, enabled = enabled, debounceMillis = 0) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "翌月")
        }
    }
}
