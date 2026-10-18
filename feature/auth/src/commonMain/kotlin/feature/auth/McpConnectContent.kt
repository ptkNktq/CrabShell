package feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import core.ui.components.AppButton
import core.ui.components.AppTextButton
import core.ui.theme.AppTheme

@Composable
internal fun McpConnectContent(
    isCompleting: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    McpConnectFrame(title = "AI アプリとの連携") {
        Text(
            text =
                "Claude などの AI アプリから、あなたのアカウントでごはんの記録を見たり付けたりできるようにします。" +
                    "次の画面に連携するアプリ名が表示されるので、自分で操作したものか確認してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "心当たりがない場合は「キャンセル」を押してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (errorMessage != null) {
            SelectionContainer {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AppButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isCompleting,
        ) {
            if (isCompleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("連携を続ける")
            }
        }

        AppTextButton(
            onClick = onCancel,
            enabled = !isCompleting,
        ) {
            Text("キャンセル")
        }
    }
}

/** 連携をキャンセルした後の画面。連携のために開いたタブなので、閉じてもらうだけにする */
@Composable
internal fun McpConnectCancelledContent() {
    McpConnectFrame(title = "キャンセルしました") {
        Text(
            text = "AI アプリとの連携は行われていません。このタブを閉じてください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 連携画面の共通の枠（中央のカード、アイコン、タイトル） */
@Composable
private fun McpConnectFrame(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )

                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        content()
                    }
                }
            }
        }
    }
}
