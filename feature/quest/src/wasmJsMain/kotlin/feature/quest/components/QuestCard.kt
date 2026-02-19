package feature.quest.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import core.ui.extensions.icon
import core.ui.extensions.label
import model.Quest
import model.QuestStatus

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
            // ヘッダー: カテゴリアイコン + ステータスバッジ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = quest.category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = quest.category.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = quest.status)
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // タイトル
            Text(
                text = quest.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // RPG風テキスト
            if (quest.description.isNotBlank()) {
                Text(
                    text = quest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 報酬ポイント
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${quest.rewardPoints} pt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // メタ情報
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "依頼者: ${quest.creatorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quest.assigneeName?.let {
                    Text(
                        text = "受注者: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                quest.deadline?.let {
                    Text(
                        text = "期限: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // アクションボタン
            Spacer(Modifier.weight(1f))
            QuestActionButton(
                quest = quest,
                isCreator = isCreator,
                isAssignee = isAssignee,
                onAccept = onAccept,
                onComplete = onComplete,
                onVerify = onVerify,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: QuestStatus) {
    val (containerColor, contentColor) =
        when (status) {
            QuestStatus.Open ->
                MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            QuestStatus.Accepted ->
                MaterialTheme.colorScheme.secondaryContainer to
                    MaterialTheme.colorScheme.onSecondaryContainer
            QuestStatus.Completed ->
                MaterialTheme.colorScheme.tertiaryContainer to
                    MaterialTheme.colorScheme.onTertiaryContainer
            QuestStatus.Verified ->
                MaterialTheme.colorScheme.primaryContainer to
                    MaterialTheme.colorScheme.onPrimaryContainer
            QuestStatus.Expired ->
                MaterialTheme.colorScheme.errorContainer to
                    MaterialTheme.colorScheme.onErrorContainer
        }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

@Composable
private fun QuestActionButton(
    quest: Quest,
    isCreator: Boolean,
    isAssignee: Boolean,
    onAccept: () -> Unit,
    onComplete: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit,
) {
    when (quest.status) {
        QuestStatus.Open -> {
            if (isCreator) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "削除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("受注する")
                }
            }
        }
        QuestStatus.Accepted -> {
            if (isAssignee) {
                OutlinedButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("達成報告")
                }
            } else {
                Spacer(Modifier.height(0.dp))
            }
        }
        QuestStatus.Completed -> {
            if (isCreator) {
                Button(
                    onClick = onVerify,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                ) {
                    Text("承認する")
                }
            } else {
                Spacer(Modifier.height(0.dp))
            }
        }
        QuestStatus.Verified,
        QuestStatus.Expired,
        -> {
            Spacer(Modifier.height(0.dp))
        }
    }
}
