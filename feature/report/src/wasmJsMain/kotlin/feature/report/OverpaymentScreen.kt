package feature.report

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    )
}

@Composable
internal fun OverpaymentContent(
    balances: List<UserBalance>,
    period: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    isCompact: Boolean = false,
) {
    val outerPadding = if (isCompact) 12.dp else 24.dp

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(outerPadding),
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
    }
}
