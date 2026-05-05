package feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import core.ui.util.openExternalUrl

private val externalUrlLinkInteractionListener =
    LinkInteractionListener { link ->
        if (link is LinkAnnotation.Url) {
            openExternalUrl(link.url)
        }
    }

@Composable
internal fun CreditsCard(modifier: Modifier = Modifier) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "サードパーティ ライセンス",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = "本アプリで利用しているサードパーティ製のデータ・ライブラリ等のクレジットです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        linkInteractionListener = externalUrlLinkInteractionListener,
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
                        linkInteractionListener = externalUrlLinkInteractionListener,
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
