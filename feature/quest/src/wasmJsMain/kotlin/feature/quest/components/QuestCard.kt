package feature.quest.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ui.extensions.icon
import core.ui.extensions.label
import model.Quest
import model.QuestStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestCard(
    quest: Quest,
    currentUserUid: String,
    onAccept: () -> Unit,
    onComplete: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCreator = quest.creatorUid == currentUserUid
    val isAssignee = quest.assigneeUid == currentUserUid

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ヘッダー: タイトル + 報酬
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = quest.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${quest.rewardPoints}pt",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // カテゴリ + ステータス
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(quest.category.label) },
                    leadingIcon = {
                        Icon(
                            quest.category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                AssistChip(
                    onClick = {},
                    label = { Text(quest.status.label) },
                )
            }

            // RPG風テキスト
            if (quest.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = quest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // メタ情報
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "依頼者: ${quest.creatorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quest.deadline?.let {
                    Text(
                        text = "期限: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            quest.assigneeName?.let {
                Text(
                    text = "受注者: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            // アクションボタン
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (quest.status) {
                    QuestStatus.Open -> {
                        if (!isCreator) {
                            Button(onClick = onAccept) {
                                Text("受注する")
                            }
                        }
                        if (isCreator) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "削除",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    QuestStatus.Accepted -> {
                        if (isAssignee) {
                            OutlinedButton(onClick = onComplete) {
                                Text("達成報告")
                            }
                        }
                    }
                    QuestStatus.Completed -> {
                        if (isCreator) {
                            Button(
                                onClick = onVerify,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                    ),
                            ) {
                                Text("承認する")
                            }
                        }
                    }
                    QuestStatus.Verified,
                    QuestStatus.Expired,
                    -> {}
                }
            }
        }
    }
}
