package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import core.ui.components.AppButton
import core.ui.components.AppIconButton
import core.ui.util.formatIsoToJst
import model.PasskeyCredentialInfo
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AccountScreen(modifier: Modifier = Modifier) {
    val passwordVm: PasswordChangeViewModel = koinViewModel()
    val passkeyVm: PasskeyManagementViewModel = koinViewModel()
    val loginHistoryVm: LoginHistoryViewModel = koinViewModel()

    AccountContent(
        passwordState = passwordVm.uiState,
        onCurrentPasswordChanged = passwordVm::onCurrentPasswordChanged,
        onNewPasswordChanged = passwordVm::onNewPasswordChanged,
        onConfirmPasswordChanged = passwordVm::onConfirmPasswordChanged,
        onChangePassword = passwordVm::onChangePassword,
        passkeyState = passkeyVm.uiState,
        onRegisterPasskey = passkeyVm::onRegisterPasskey,
        onDeletePasskey = passkeyVm::onDeletePasskey,
        loginHistoryState = loginHistoryVm.uiState,
        onRetryLoginHistory = loginHistoryVm::loadHistory,
        modifier = modifier,
    )
}

@Composable
internal fun AccountContent(
    passwordState: PasswordChangeUiState,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onChangePassword: () -> Unit,
    passkeyState: PasskeyManagementUiState,
    onRegisterPasskey: () -> Unit,
    onDeletePasskey: (Long) -> Unit,
    loginHistoryState: LoginHistoryUiState,
    onRetryLoginHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PasswordChangeCard(
            currentPassword = passwordState.currentPassword,
            newPassword = passwordState.newPassword,
            confirmPassword = passwordState.confirmPassword,
            isLoading = passwordState.isLoading,
            errorMessage = passwordState.errorMessage,
            successMessage = passwordState.successMessage,
            onCurrentPasswordChanged = onCurrentPasswordChanged,
            onNewPasswordChanged = onNewPasswordChanged,
            onConfirmPasswordChanged = onConfirmPasswordChanged,
            onChangePassword = onChangePassword,
            modifier = Modifier.fillMaxWidth(),
        )

        if (passkeyState.isAvailable) {
            PasskeyManagementCard(
                credentialCount = passkeyState.credentialCount,
                credentials = passkeyState.credentials,
                isRegistering = passkeyState.isRegistering,
                deletingCredentialId = passkeyState.deletingCredentialId,
                errorMessage = passkeyState.errorMessage,
                successMessage = passkeyState.successMessage,
                onRegisterPasskey = onRegisterPasskey,
                onDeletePasskey = onDeletePasskey,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LoginHistoryCardContent(
            isLoading = loginHistoryState.isLoading,
            loadError = loginHistoryState.loadError,
            loadErrorMessage = loginHistoryState.loadErrorMessage,
            events = loginHistoryState.events,
            onRetry = onRetryLoginHistory,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PasswordChangeCard(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onCurrentPasswordChanged: (String) -> Unit,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onChangePassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "パスワード変更", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }

            OutlinedTextField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChanged,
                label = { Text("現在のパスワード") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    AppIconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }, debounceMillis = 0) {
                        Icon(
                            if (currentPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (currentPasswordVisible) "パスワードを隠す" else "パスワードを表示",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = onNewPasswordChanged,
                label = { Text("新しいパスワード") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    AppIconButton(onClick = { newPasswordVisible = !newPasswordVisible }, debounceMillis = 0) {
                        Icon(
                            if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (newPasswordVisible) "パスワードを隠す" else "パスワードを表示",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChanged,
                label = { Text("新しいパスワード（確認）") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    AppIconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }, debounceMillis = 0) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "パスワードを隠す" else "パスワードを表示",
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            if (errorMessage != null) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (successMessage != null) {
                Text(text = successMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            AppButton(
                onClick = onChangePassword,
                modifier = Modifier.height(48.dp),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("変更する")
                }
            }
        }
    }
}

@Composable
private fun PasskeyManagementCard(
    credentialCount: Int,
    credentials: List<PasskeyCredentialInfo>,
    isRegistering: Boolean,
    deletingCredentialId: Long?,
    errorMessage: String?,
    successMessage: String?,
    onRegisterPasskey: () -> Unit,
    onDeletePasskey: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = "パスキー管理", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }

            Text(
                text = "登録済み: $credentialCount 件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "別の端末やブラウザからログインするには、パスキーを追加してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (credentials.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    credentials.forEachIndexed { index, credential ->
                        PasskeyCredentialRow(
                            credential = credential,
                            isDeleting = deletingCredentialId == credential.id,
                            onDelete = { onDeletePasskey(credential.id) },
                        )
                        if (index < credentials.lastIndex) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (successMessage != null) {
                Text(text = successMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }

            AppButton(
                onClick = onRegisterPasskey,
                modifier = Modifier.height(48.dp),
                enabled = !isRegistering,
            ) {
                if (isRegistering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("パスキーを追加")
                }
            }
        }
    }
}

@Composable
private fun PasskeyCredentialRow(
    credential: PasskeyCredentialInfo,
    isDeleting: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "登録日: ${formatIsoToJst(credential.createdAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val transportLabels = credential.transports.map { transportLabel(it) }
            if (transportLabels.isNotEmpty()) {
                Text(
                    text = transportLabels.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AppIconButton(onClick = onDelete, enabled = !isDeleting) {
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** WebAuthn の transport 値を日本語表示に変換する */
private fun transportLabel(transport: String): String =
    when (transport) {
        "internal" -> "この端末"
        "usb" -> "USBセキュリティキー"
        "nfc" -> "NFC"
        "ble" -> "Bluetooth"
        "hybrid" -> "ハイブリッド"
        "smart-card" -> "スマートカード"
        else -> transport
    }
