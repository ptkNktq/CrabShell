package core.ui.util

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import core.common.openExternalUrl

/**
 * [LinkAnnotation.Url] を新規タブで開く共有 [LinkInteractionListener]。
 * デフォルトの [androidx.compose.ui.platform.LocalUriHandler] は wasmJs で同タブ遷移し SPA をアンロードするため、
 * このリスナーを `LinkAnnotation.Url(linkInteractionListener = ExternalUrlLinkInteractionListener)` として渡す。
 */
val ExternalUrlLinkInteractionListener: LinkInteractionListener =
    LinkInteractionListener { link ->
        if (link is LinkAnnotation.Url) {
            openExternalUrl(link.url)
        }
    }
