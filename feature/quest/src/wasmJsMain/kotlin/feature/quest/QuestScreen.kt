package feature.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import model.PointHistory
import model.Quest
import model.QuestCategory
import model.Reward
import model.UserPoints
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun QuestScreen(vm: QuestViewModel = koinViewModel()) {
    val windowSizeClass = LocalWindowSizeClass.current
    val currentUserUid =
        (AuthStateHolder.state as? AuthState.Authenticated)?.user?.uid ?: ""
    val isAdmin =
        (AuthStateHolder.state as? AuthState.Authenticated)?.user?.isAdmin == true

    QuestBoardContent(
        quests = vm.uiState.quests,
        isLoading = vm.uiState.isLoading,
        error = vm.uiState.error,
        isCreating = vm.uiState.isCreating,
        canCreateQuest = vm.uiState.canCreateQuest,
        isAiAvailable = vm.uiState.isAiAvailable,
        isGenerating = vm.uiState.isGenerating,
        currentUserUid = currentUserUid,
        currentTab = vm.uiState.currentTab,
        myPoints = vm.uiState.myPoints,
        rewards = vm.uiState.rewards,
        history = vm.uiState.history,
        isAdmin = isAdmin,
        isCreatingReward = vm.uiState.isCreatingReward,
        onSelectTab = vm::onSelectTab,
        onToggleCreateForm = vm::onToggleCreateForm,
        onCreateQuest = vm::onCreateQuest,
        onGenerateText = vm::onGenerateText,
        onAcceptQuest = vm::onAcceptQuest,
        onVerifyQuest = vm::onVerifyQuest,
        onDeleteQuest = vm::onDeleteQuest,
        onExchangeReward = vm::onExchangeReward,
        onToggleCreateReward = vm::onToggleCreateReward,
        onCreateReward = vm::onCreateReward,
        onDeleteReward = vm::onDeleteReward,
        onDismissError = vm::onDismissError,
        windowSizeClass = windowSizeClass,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestBoardContent(
    quests: List<Quest>,
    isLoading: Boolean,
    error: String?,
    isCreating: Boolean,
    canCreateQuest: Boolean,
    isAiAvailable: Boolean,
    isGenerating: Boolean,
    currentUserUid: String,
    currentTab: QuestTab,
    myPoints: UserPoints?,
    rewards: List<Reward>,
    history: List<PointHistory>,
    isAdmin: Boolean,
    isCreatingReward: Boolean,
    onSelectTab: (QuestTab) -> Unit,
    onToggleCreateForm: () -> Unit,
    onCreateQuest: (String, String, QuestCategory, Int, String?) -> Unit,
    onGenerateText: (String, QuestCategory, Int, String?, (String) -> Unit) -> Unit,
    onAcceptQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
    onExchangeReward: (String) -> Unit,
    onToggleCreateReward: () -> Unit,
    onCreateReward: (String, String, Int) -> Unit,
    onDeleteReward: (String) -> Unit,
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
        // ヘッダー: タイトル + ポイント残高
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "クエスト掲示板",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            myPoints?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${it.balance}pt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // タブ切り替え
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            val tabs =
                listOf(
                    QuestTab.Board to "掲示板",
                    QuestTab.Rewards to "報酬交換",
                    QuestTab.History to "ポイント履歴",
                )
            tabs.forEach { (tab, label) ->
                FilterChip(
                    selected = currentTab == tab,
                    onClick = { onSelectTab(tab) },
                    label = { Text(label) },
                )
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

        when (currentTab) {
            QuestTab.Board ->
                BoardTab(
                    quests = quests,
                    isLoading = isLoading,
                    canCreateQuest = canCreateQuest,
                    isAiAvailable = isAiAvailable,
                    isGenerating = isGenerating,
                    currentUserUid = currentUserUid,
                    onCreateQuest = onCreateQuest,
                    onGenerateText = onGenerateText,
                    onAcceptQuest = onAcceptQuest,
                    onVerifyQuest = onVerifyQuest,
                    onDeleteQuest = onDeleteQuest,
                    isCompact = isCompact,
                    modifier = Modifier.weight(1f),
                )

            QuestTab.Rewards ->
                RewardsTab(
                    rewards = rewards,
                    myPoints = myPoints,
                    isLoading = isLoading,
                    currentUserUid = currentUserUid,
                    isAdmin = isAdmin,
                    isCreatingReward = isCreatingReward,
                    onExchange = onExchangeReward,
                    onToggleCreateReward = onToggleCreateReward,
                    onCreateReward = onCreateReward,
                    onDeleteReward = onDeleteReward,
                    modifier = Modifier.weight(1f),
                )

            QuestTab.History ->
                HistoryTab(
                    history = history,
                    isLoading = isLoading,
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

@Composable
private fun BoardTab(
    quests: List<Quest>,
    isLoading: Boolean,
    canCreateQuest: Boolean,
    isAiAvailable: Boolean,
    isGenerating: Boolean,
    currentUserUid: String,
    onCreateQuest: (String, String, QuestCategory, Int, String?) -> Unit,
    onGenerateText: (String, QuestCategory, Int, String?, (String) -> Unit) -> Unit,
    onAcceptQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
    isCompact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isCompact) {
        // Compact: 縦スクロール、フォームはインラインで展開
        var showForm by remember { mutableStateOf(false) }
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            if (showForm) {
                CreateQuestForm(
                    onSubmit = { title, desc, cat, pts, deadline ->
                        onCreateQuest(title, desc, cat, pts, deadline)
                        showForm = false
                    },
                    onCancel = { showForm = false },
                    showCloseButton = true,
                    enabled = canCreateQuest,
                    isAiAvailable = isAiAvailable,
                    isGenerating = isGenerating,
                    onGenerateText = onGenerateText,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Button(
                    onClick = { showForm = true },
                    enabled = canCreateQuest,
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text("クエスト作成")
                }
            }

            QuestListInline(
                quests = quests,
                isLoading = isLoading,
                currentUserUid = currentUserUid,
                onAcceptQuest = onAcceptQuest,
                onVerifyQuest = onVerifyQuest,
                onDeleteQuest = onDeleteQuest,
            )
        }
    } else {
        // Expanded: 左にクエスト一覧、右に投稿フォーム
        Row(modifier = modifier.fillMaxWidth()) {
            // 左カラム: クエスト一覧
            QuestListContent(
                quests = quests,
                isLoading = isLoading,
                currentUserUid = currentUserUid,
                onAcceptQuest = onAcceptQuest,
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
                    isAiAvailable = isAiAvailable,
                    isGenerating = isGenerating,
                    onGenerateText = onGenerateText,
                )
            }
        }
    }
}

@Composable
private fun QuestListContent(
    quests: List<Quest>,
    isLoading: Boolean,
    currentUserUid: String,
    onAcceptQuest: (String) -> Unit,
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
                        onVerify = { onVerifyQuest(quest.id) },
                        onDelete = { onDeleteQuest(quest.id) },
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        }
    }
}

/** Compact 用: スクロールは親が担当するため自前でスクロールしない */
@Composable
private fun QuestListInline(
    quests: List<Quest>,
    isLoading: Boolean,
    currentUserUid: String,
    onAcceptQuest: (String) -> Unit,
    onVerifyQuest: (String) -> Unit,
    onDeleteQuest: (String) -> Unit,
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        quests.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                quests.forEach { quest ->
                    QuestCard(
                        quest = quest,
                        currentUserUid = currentUserUid,
                        onAccept = { onAcceptQuest(quest.id) },
                        onVerify = { onVerifyQuest(quest.id) },
                        onDelete = { onDeleteQuest(quest.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardsTab(
    rewards: List<Reward>,
    myPoints: UserPoints?,
    isLoading: Boolean,
    currentUserUid: String,
    isAdmin: Boolean,
    isCreatingReward: Boolean,
    onExchange: (String) -> Unit,
    onToggleCreateReward: () -> Unit,
    onCreateReward: (String, String, Int) -> Unit,
    onDeleteReward: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isCreatingReward) {
            Button(
                onClick = onToggleCreateReward,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text("報酬追加")
            }
        }

        if (isCreatingReward) {
            CreateRewardForm(
                onSubmit = onCreateReward,
                onCancel = onToggleCreateReward,
            )
        }

        // 報酬一覧
        Text(
            "報酬一覧",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (rewards.isEmpty()) {
            Text(
                "報酬はまだ登録されていません",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rewards.forEach { reward ->
            RewardCard(
                reward = reward,
                canExchange = (myPoints?.balance ?: 0) >= reward.cost && reward.isAvailable,
                canDelete = reward.creatorUid == currentUserUid || isAdmin,
                onExchange = { onExchange(reward.id) },
                onDelete = { onDeleteReward(reward.id) },
            )
        }
    }
}

@Composable
private fun RewardCard(
    reward: Reward,
    canExchange: Boolean,
    canDelete: Boolean,
    onExchange: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reward.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (reward.description.isNotBlank()) {
                    Text(
                        reward.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${reward.cost}pt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row {
                Button(
                    onClick = onExchange,
                    enabled = canExchange,
                ) {
                    Text("交換")
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "削除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateRewardForm(
    onSubmit: (String, String, Int) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "新しい報酬",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("報酬名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("説明（任意）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = costText,
                onValueChange = { costText = it.filter { c -> c.isDigit() } },
                label = { Text("必要ポイント") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("キャンセル") }
                Button(
                    onClick = { onSubmit(name, description, costText.toIntOrNull() ?: 0) },
                    enabled = name.isNotBlank() && (costText.toIntOrNull() ?: 0) > 0,
                ) {
                    Text("追加")
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<PointHistory>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (history.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "ポイント履歴はありません",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        history.forEach { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            entry.timestamp.take(10),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (entry.amount > 0) "+${entry.amount}pt" else "${entry.amount}pt",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (entry.amount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
            }
        }
    }
}
