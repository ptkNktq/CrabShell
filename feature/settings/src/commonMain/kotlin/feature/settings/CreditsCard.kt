package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import core.ui.util.ExternalUrlLinkInteractionListener

@Composable
internal fun CreditsCard(
    isLoading: Boolean = false,
    libraries: List<Library> = emptyList(),
    error: String? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val linkStyles =
        remember(primaryColor) {
            TextLinkStyles(
                style =
                    SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
        }
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val licenseLinkStyles =
        remember(onSurfaceVariantColor) {
            TextLinkStyles(
                style =
                    SpanStyle(
                        color = onSurfaceVariantColor,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
        }

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "サードパーティ ライセンス",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "本アプリで利用しているサードパーティ製のデータ・ライブラリ等のクレジットです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // データクレジット
            Text(
                text = "データ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            CreditEntry(
                name = "GeoLite2-City",
                provider = "MaxMind",
                providerUrl = "https://www.maxmind.com",
                description = "ログイン履歴に表示する国・地域・都市のジオロケーション情報",
                licenseLabel = "CC BY-SA 4.0",
                licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                attribution = "This product includes GeoLite2 Data created by MaxMind, available from https://www.maxmind.com.",
                linkStyles = linkStyles,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // OSS ライブラリ一覧（セクションラベルはロード中・エラー中も常時表示してコンテキストを提供）
            Text(
                text = "OSS ライブラリ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                        strokeWidth = 2.dp,
                    )
                }

                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("再読み込み")
                    }
                }

                libraries.isEmpty() -> {
                    Text(
                        text = "ライセンス情報がありません",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        text = "${libraries.size} 件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column {
                        libraries.forEachIndexed { index, library ->
                            LibraryEntry(
                                library = library,
                                linkStyles = linkStyles,
                                licenseLinkStyles = licenseLinkStyles,
                            )
                            if (index < libraries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    thickness = 0.5.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEntry(
    library: Library,
    linkStyles: TextLinkStyles,
    licenseLinkStyles: TextLinkStyles,
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val nameText =
                buildAnnotatedString {
                    val url =
                        library.website?.takeIf { it.isNotBlank() }
                            ?: library.scm?.url?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = linkStyles,
                                linkInteractionListener = ExternalUrlLinkInteractionListener,
                            ),
                        ) {
                            append(library.name)
                        }
                    } else {
                        append(library.name)
                    }
                }
            Text(
                text = nameText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            library.artifactVersion?.let { version ->
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        val licenseText =
            buildAnnotatedString {
                library.licenses.forEachIndexed { index, license ->
                    if (index > 0) append(" / ")
                    val url = license.url?.takeIf { it.isNotBlank() }
                    if (url != null) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = licenseLinkStyles,
                                linkInteractionListener = ExternalUrlLinkInteractionListener,
                            ),
                        ) {
                            append(license.name)
                        }
                    } else {
                        append(license.name)
                    }
                }
            }
        if (licenseText.isNotEmpty()) {
            Text(
                text = licenseText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreditEntry(
    name: String,
    provider: String,
    providerUrl: String,
    description: String,
    licenseLabel: String,
    licenseUrl: String,
    linkStyles: TextLinkStyles,
    attribution: String? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val title =
            buildAnnotatedString {
                append(name)
                append(" by ")
                withLink(
                    LinkAnnotation.Url(
                        url = providerUrl,
                        styles = linkStyles,
                        linkInteractionListener = ExternalUrlLinkInteractionListener,
                    ),
                ) {
                    append(provider)
                }
            }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (attribution != null) {
            Text(
                text = attribution,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val license =
            buildAnnotatedString {
                append("ライセンス: ")
                withLink(
                    LinkAnnotation.Url(
                        url = licenseUrl,
                        styles = linkStyles,
                        linkInteractionListener = ExternalUrlLinkInteractionListener,
                    ),
                ) {
                    append(licenseLabel)
                }
            }
        Text(
            text = license,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
