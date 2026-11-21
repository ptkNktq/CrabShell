package feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import core.ui.components.AppOutlinedButton
import core.ui.components.AppTextButton
import core.ui.util.formatIsoToJst
import model.McpConnection

// 畳んだ状態で出す件数（無効にした日の新しい順の先頭。連携し直すのは最近無効にしたアプリである見込みが高い）
internal const val DISABLED_COLLAPSED_COUNT = 3

// 開いたときに絞り込み欄を出す件数の下限（これより少なければ一覧を見るだけで足りる）
internal const val DISABLED_FILTER_THRESHOLD = 10

/**
 * 連携画面に出す、無効にした連携アプリの状態。
 *
 * @property connections 無効にした連携アプリ（無効にした日の新しい順）
 * @property isExpanded 先頭の [DISABLED_COLLAPSED_COUNT] 件より後も表示しているか
 * @property filterQuery アプリ名の絞り込み
 * @property enablingId 有効化を実行中の連携アプリの ID
 * @property enabledIds この画面で有効にした連携アプリの ID（押せない「有効にしました」の表示にする）
 */
data class DisabledConnectionsState(
    val connections: List<McpConnection> = emptyList(),
    val isExpanded: Boolean = false,
    val filterQuery: String = "",
    val enablingId: String? = null,
    val enabledIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
) {
    /** まだ無効のままの件数（この画面で有効にした分は除く） */
    val remainingDisabledCount: Int get() = connections.count { it.id !in enabledIds }

    /** 絞り込み欄を出すか（開いていて、件数が多い場合だけ） */
    val showsFilter: Boolean get() = isExpanded && connections.size > DISABLED_FILTER_THRESHOLD

    /** 「ほか N 件を表示」の N。畳んでいない、または先頭だけで全件の場合は 0 */
    val hiddenCount: Int get() = if (isExpanded) 0 else (connections.size - DISABLED_COLLAPSED_COUNT).coerceAtLeast(0)

    /** 表示する連携アプリ。畳んでいれば先頭だけ、絞り込み中はアプリ名に含むものだけ */
    val visibleConnections: List<McpConnection>
        get() {
            if (!isExpanded) return connections.take(DISABLED_COLLAPSED_COUNT)
            val query = filterQuery.trim()
            if (!showsFilter || query.isEmpty()) return connections
            return connections.filter { it.clientName.contains(query, ignoreCase = true) }
        }
}

/**
 * 無効にした連携アプリがあることの案内と、その場での有効化。
 *
 * どのアプリが連携しようとしているかは CrabShell からは分からない（Login URI に client_id が来ない）ため、
 * 無効にしたアプリを挙げて、連携し直すアプリを本人に選んで有効にしてもらう。
 * 無効のまま続けると、連携は完了してもそのアプリからは使えない。
 */
@Composable
internal fun DisabledConnectionsNotice(
    state: DisabledConnectionsState,
    onToggleExpanded: () -> Unit,
    onFilterQueryChange: (String) -> Unit,
    onEnable: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 連携の完了処理中など、画面全体の操作を止めている間は false にする */
    enabled: Boolean = true,
) {
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
                Text(
                    text =
                        if (state.remainingDisabledCount > 0) {
                            "無効にしたアプリが ${state.remainingDisabledCount} 件あります"
                        } else {
                            "無効にしたアプリはすべて有効にしました"
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                )
            }
            // すべて有効にした後は、有効にするよう促す案内は要らない
            if (state.remainingDisabledCount > 0) {
                Text(
                    text = "連携し直すアプリは、ここで有効にしてから続けてください。無効のまま続けても、そのアプリからは使えません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }

            if (state.showsFilter) {
                OutlinedTextField(
                    value = state.filterQuery,
                    onValueChange = onFilterQueryChange,
                    placeholder = { Text("アプリ名で絞り込み") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = contentColor,
                            unfocusedTextColor = contentColor,
                        ),
                )
            }

            val visible = state.visibleConnections
            // 開いたときは高さに上限を付けて案内の中だけスクロールさせ、「連携を続ける」を画面の外へ押し出さない
            val listModifier =
                if (state.isExpanded) {
                    Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                }
            Column(modifier = listModifier) {
                if (visible.isEmpty()) {
                    Text(
                        text = "一致するアプリはありません",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
                visible.forEachIndexed { index, connection ->
                    DisabledConnectionRow(
                        connection = connection,
                        isEnabling = state.enablingId == connection.id,
                        isEnabled = connection.id in state.enabledIds,
                        // 実行中は他の行も押せなくし、結果の表示が入れ替わらないようにする
                        buttonEnabled = enabled && state.enablingId == null,
                        onEnable = { onEnable(connection.id) },
                    )
                    if (index < visible.lastIndex) {
                        HorizontalDivider(thickness = 1.dp, color = contentColor.copy(alpha = 0.2f))
                    }
                }
            }

            if (state.errorMessage != null) {
                Text(text = state.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.connections.size > DISABLED_COLLAPSED_COUNT) {
                AppTextButton(onClick = onToggleExpanded, debounceMillis = 0) {
                    Icon(
                        imageVector = if (state.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (state.isExpanded) "閉じる" else "ほか ${state.hiddenCount} 件を表示",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DisabledConnectionRow(
    connection: McpConnection,
    isEnabling: Boolean,
    isEnabled: Boolean,
    buttonEnabled: Boolean,
    onEnable: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = connection.clientName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            connection.disabledAt?.let { disabledAt ->
                Text(
                    text = "無効化: ${formatIsoToJst(disabledAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }

        if (isEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = "有効にしました", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            AppOutlinedButton(onClick = onEnable, enabled = buttonEnabled) {
                if (isEnabling) {
                    // 実行中はボタンが無効になり中身も無効色になるため、スピナーの色は明示する
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text("有効にする")
                }
            }
        }
    }
}
