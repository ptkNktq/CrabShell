package feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(if (isCompact) 12.dp else 24.dp),
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
            if (canCreateQuest && !isCreating) {
                Button(onClick = onToggleCreateForm) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("クエスト作成")
                }
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
                if (isCompact) {
                    // Compact: 縦に並べる
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        quests.forEach { quest ->
                            QuestCard(
                                quest = quest,
                                currentUserUid = currentUserUid,
                                onAccept = { onAcceptQuest(quest.id) },
                                onComplete = { onCompleteQuest(quest.id) },
                                onVerify = { onVerifyQuest(quest.id) },
                                onDelete = { onDeleteQuest(quest.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    // Expanded: 横に並べる（最大3枚）
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        quests.forEach { quest ->
                            QuestCard(
                                quest = quest,
                                currentUserUid = currentUserUid,
                                onAccept = { onAcceptQuest(quest.id) },
                                onComplete = { onCompleteQuest(quest.id) },
                                onVerify = { onVerifyQuest(quest.id) },
                                onDelete = { onDeleteQuest(quest.id) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        // 空スロットで幅を揃える
                        repeat(3 - quests.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
