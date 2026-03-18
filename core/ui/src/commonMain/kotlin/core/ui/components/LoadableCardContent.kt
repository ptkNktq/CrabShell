package core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadableCardContent(
    isLoading: Boolean,
    loadError: Boolean,
    loadErrorMessage: String? = null,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        loadError -> {
            LoadErrorContent(onRetry = onRetry, errorDetail = loadErrorMessage)
        }
        else -> {
            content()
        }
    }
}
