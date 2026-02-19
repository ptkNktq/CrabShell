package feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import core.ui.extensions.label
import feature.quest.components.CreateQuestForm
import feature.quest.components.QuestCard
import model.Quest
import model.QuestCategory
import model.QuestStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun QuestScreen(vm: QuestViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current
    val currentUserUid =
        (AuthStateHolder.state as? AuthState.Authenticated)?.user?.uid ?: ""

    QuestBoardContent(
        quests = vm.uiState.quests,
        selectedCategory = vm.uiState.selectedCategory,
        selectedStatus = vm.uiState.selectedStatus,
        isLoading = vm.uiState.isLoading,
        error = vm.uiState.error,
        isCreating = vm.uiState.isCreating,
        currentUserUid = currentUserUid,
        onFilterCategory = vm::onFilterCategory,
        onFilterStatus = vm::onFilterStatus,
        onToggleCreateForm = vm::onToggleCreateForm,
        onCreateQuest = vm::onCreateQuest,
        onAcceptQuest = vm::onAcceptQuest,
        onCompleteQuest = vm::onCompleteQuest,
        onVerifyQuest = vm::onVerifyQuest,
        onDeleteQuest = vm::onDeleteQuest,
        onDismissError = vm::onDismissError,
        windowSizeClass = windowSizeClass,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestBoardContent(
    quests: List<Quest>,
    selectedCategory: QuestCategory?,
    selectedStatus: QuestStatus?,
    isLoading: Boolean,
    error: String?,
    isCreating: Boolean,
    currentUserUid: String,
    onFilterCategory: (QuestCategory?) -> Unit,
    onFilterStatus: (QuestStatus?) -> Unit,
    onToggleCreateForm: () -> Unit,
    onCreateQuest: (String, String, QuestCategory, Int, String?) -> Unit,
    onAcceptQuest: (String) -> Unit,
    onCompleteQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
    onDismissError: () -> Unit,
    windowSizeClass: WindowSizeClass = WindowSizeClass.Expanded,
) {
    val isCompact = windowSizeClass == WindowSizeClass.Compact

    Scaffold(
        floatingActionButton = {
            if (!isCreating) {
                ExtendedFloatingActionButton(
                    onClick = onToggleCreateForm,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("クエスト作成") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(if (isCompact) 12.dp else 24.dp),
        ) {
            Text("クエスト掲示板", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            // ステータスフィルタ
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val statusFilters = listOf(null to "すべて") + QuestStatus.entries.map { it to it.label }
                statusFilters.forEach { (status, label) ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { onFilterStatus(status) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // カテゴリフィルタ
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onFilterCategory(null) },
                    label = { Text("すべて") },
                )
                QuestCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onFilterCategory(cat) },
                        label = { Text(cat.label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // エラー表示
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            // 作成フォーム
            if (isCreating) {
                CreateQuestForm(
                    onSubmit = onCreateQuest,
                    onCancel = onToggleCreateForm,
                )
                Spacer(Modifier.height(16.dp))
            }

            // コンテンツ
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                quests.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "クエストはありません",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(quests, key = { it.id }) { quest ->
                            QuestCard(
                                quest = quest,
                                currentUserUid = currentUserUid,
                                onAccept = { onAcceptQuest(quest.id) },
                                onComplete = { onCompleteQuest(quest.id) },
                                onVerify = { onVerifyQuest(quest.id) },
                                onDelete = { onDeleteQuest(quest.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
