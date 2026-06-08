package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.components.LoadableCardContent
import model.User
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun UserManagementScreen(modifier: Modifier = Modifier) {
    val vm: UserNameViewModel = koinViewModel()
    val s = vm.uiState

    UserNameManagementCard(
        isLoading = s.isLoading,
        loadError = s.loadError,
        loadErrorMessage = s.loadErrorMessage,
        users = s.users,
        usersSaving = s.isSaving,
        usersMessage = s.message,
        onUpdateDisplayName = vm::onUpdateDisplayName,
        onRetry = vm::loadUsers,
        modifier = modifier,
    )
}

@Composable
private fun UserNameManagementCard(
    isLoading: Boolean = false,
    loadError: Boolean = false,
    loadErrorMessage: String? = null,
    users: List<User>,
    usersSaving: Boolean,
    usersMessage: String?,
    onUpdateDisplayName: (String, String) -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editedNames by remember(users) {
        mutableStateOf(users.associate { it.uid to (it.displayName ?: "") })
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        LoadableCardContent(
            isLoading = isLoading,
            loadError = loadError,
            loadErrorMessage = loadErrorMessage,
            onRetry = onRetry,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                for (user in users) {
                    OutlinedTextField(
                        value = editedNames[user.uid] ?: "",
                        onValueChange = { value ->
                            editedNames = editedNames.toMutableMap().apply { put(user.uid, value) }
                        },
                        label = { Text(user.uid) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !usersSaving,
                    )
                }

                if (usersMessage != null) {
                    Text(text = usersMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Button(
                    onClick = {
                        for (user in users) {
                            val newName = editedNames[user.uid] ?: ""
                            val oldName = user.displayName ?: ""
                            if (newName != oldName) onUpdateDisplayName(user.uid, newName)
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    enabled = !usersSaving,
                ) {
                    if (usersSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("保存する")
                    }
                }
            }
        }
    }
}
