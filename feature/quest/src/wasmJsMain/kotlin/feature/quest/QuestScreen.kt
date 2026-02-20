package feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.auth.AuthState
import core.auth.AuthStateHolder
import core.ui.LocalWindowSizeClass
import core.ui.WindowSizeClass
import feature.quest.components.CreateQuestForm
import feature.quest.components.QuestCard
import model.Quest
import model.QuestCategory
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun QuestScreen(vm: QuestViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current
    val currentUserUid =
        (AuthStateHolder.state as? AuthState.Authenticated)?.user?.uid ?: ""

    QuestBoardContent(
        quests = vm.uiState.quests,
        isLoading = vm.uiState.isLoading,
        error = vm.uiState.error,
        isCreating = vm.uiState.isCreating,
        canCreateQuest = vm.uiState.canCreateQuest,
        currentUserUid = currentUserUid,
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

@Composable
internal fun QuestBoardContent(
    quests: List<Quest>,
    isLoading: Boolean,
    error: String?,
    isCreating: Boolean,
    canCreateQuest: Boolean,
    currentUserUid: String,
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

    if (isCompact) {
        CompactLayout(
            quests = quests,
            isLoading = isLoading,
            error = error,
            isCreating = isCreating,
            canCreateQuest = canCreateQuest,
            currentUserUid = currentUserUid,
            onToggleCreateForm = onToggleCreateForm,
            onCreateQuest = onCreateQuest,
            onAcceptQuest = onAcceptQuest,
            onCompleteQuest = onCompleteQuest,
            onVerifyQuest = onVerifyQuest,
            onDeleteQuest = onDeleteQuest,
        )
    } else {
        ExpandedLayout(
            quests = quests,
            isLoading = isLoading,
            error = error,
            isCreating = isCreating,
            canCreateQuest = canCreateQuest,
            currentUserUid = currentUserUid,
            onToggleCreateForm = onToggleCreateForm,
            onCreateQuest = onCreateQuest,
            onAcceptQuest = onAcceptQuest,
            onCompleteQuest = onCompleteQuest,
            onVerifyQuest = onVerifyQuest,
            onDeleteQuest = onDeleteQuest,
        )
    }
}

@Composable
private fun ExpandedLayout(
    quests: List<Quest>,
    isLoading: Boolean,
    error: String?,
    isCreating: Boolean,
    canCreateQuest: Boolean,
    currentUserUid: String,
    onToggleCreateForm: () -> Unit,
    onCreateQuest: (String, String, QuestCategory, Int, String?) -> Unit,
    onAcceptQuest: (String) -> Unit,
    onCompleteQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        // ヘッダー
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "クエスト掲示板",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // エラー表示
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }

        // 左: クエスト一覧、右: 投稿フォーム
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            // 左カラム: クエスト一覧
            QuestListContent(
                quests = quests,
                isLoading = isLoading,
                currentUserUid = currentUserUid,
                onAcceptQuest = onAcceptQuest,
                onCompleteQuest = onCompleteQuest,
                onVerifyQuest = onVerifyQuest,
                onDeleteQuest = onDeleteQuest,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(24.dp))

            // 右カラム: 投稿フォーム（常時表示）
            Column(
                modifier =
                    Modifier
                        .width(400.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                CreateQuestForm(
                    onSubmit = onCreateQuest,
                    onCancel = {},
                    showCloseButton = false,
                    enabled = canCreateQuest,
                )
            }
        }
    }
}

@Composable
private fun CompactLayout(
    quests: List<Quest>,
    isLoading: Boolean,
    error: String?,
    isCreating: Boolean,
    canCreateQuest: Boolean,
    currentUserUid: String,
    onToggleCreateForm: () -> Unit,
    onCreateQuest: (String, String, QuestCategory, Int, String?) -> Unit,
    onAcceptQuest: (String) -> Unit,
    onCompleteQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
) {
    var showFormCompact by remember { mutableStateOf(false) }

    if (isCreating || showFormCompact) {
        // フォーム表示
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
        ) {
            CreateQuestForm(
                onSubmit = { title, desc, cat, pts, deadline ->
                    onCreateQuest(title, desc, cat, pts, deadline)
                    showFormCompact = false
                },
                onCancel = {
                    onToggleCreateForm()
                    showFormCompact = false
                },
                showCloseButton = true,
                enabled = canCreateQuest,
            )
        }
    } else {
        // リスト表示
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
        ) {
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "クエスト掲示板",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = {
                        showFormCompact = true
                        onToggleCreateForm()
                    },
                    enabled = canCreateQuest,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("クエスト作成")
                }
            }

            // エラー表示
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }

            QuestListContent(
                quests = quests,
                isLoading = isLoading,
                currentUserUid = currentUserUid,
                onAcceptQuest = onAcceptQuest,
                onCompleteQuest = onCompleteQuest,
                onVerifyQuest = onVerifyQuest,
                onDeleteQuest = onDeleteQuest,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuestListContent(
    quests: List<Quest>,
    isLoading: Boolean,
    currentUserUid: String,
    onAcceptQuest: (String) -> Unit,
    onCompleteQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        quests.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxWidth(),
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
            Column(
                modifier =
                    modifier
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                quests.forEach { quest ->
                    QuestCard(
                        quest = quest,
                        currentUserUid = currentUserUid,
                        onAccept = { onAcceptQuest(quest.id) },
                        onComplete = { onCompleteQuest(quest.id) },
                        onVerify = { onVerifyQuest(quest.id) },
                        onDelete = { onDeleteQuest(quest.id) },
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}
