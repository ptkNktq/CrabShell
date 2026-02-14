package core.ui

import androidx.compose.runtime.compositionLocalOf

enum class WindowSizeClass {
    Compact,  // < 600dp
    Medium,   // 600-840dp
    Expanded, // > 840dp
}

val LocalWindowSizeClass = compositionLocalOf { WindowSizeClass.Expanded }
