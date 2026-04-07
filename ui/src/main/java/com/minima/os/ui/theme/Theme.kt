package com.minima.os.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// === The Ethereal Interface Color System ===
// From DESIGN.md — "frosted obsidian that reacts to touch with light and depth"

object MinimaColors {
    // Surfaces — layered depth system
    val surface = Color(0xFF0D0D1A)                  // infinite background
    val surfaceContainerLowest = Color(0xFF000000)
    val surfaceContainerLow = Color(0xFF121220)       // mid-ground
    val surfaceContainer = Color(0xFF181828)           // secondary clusters
    val surfaceContainerHigh = Color(0xFF1E1E2F)      // card backgrounds
    val surfaceContainerHighest = Color(0xFF242437)    // inputs
    val surfaceBright = Color(0xFF2A2A3F)              // active interactions
    val surfaceVariant = Color(0xFF242437)

    // Primary — celestial purple
    val primary = Color(0xFFACA3FF)
    val primaryDim = Color(0xFF9B90FF)
    val primaryContainer = Color(0xFF9D93FF)
    val primaryFixed = Color(0xFF9D93FF)
    val onPrimary = Color(0xFF270498)
    val onPrimaryContainer = Color(0xFF1C007A)

    // Secondary
    val secondary = Color(0xFFB190FE)
    val secondaryDim = Color(0xFFAC8BF8)
    val secondaryContainer = Color(0xFF523099)
    val onSecondary = Color(0xFF2F0074)
    val onSecondaryContainer = Color(0xFFD9C8FF)

    // Tertiary — accent pink
    val tertiary = Color(0xFFFF9ECB)
    val tertiaryContainer = Color(0xFFFD87C1)

    // On surfaces — opacity-based hierarchy
    val onSurface = Color(0xFFE9E6F9)                 // 90% — primary info
    val onSurfaceVariant = Color(0xFFABA9BB)           // 60% — secondary info
    val outline = Color(0xFF757485)                    // 30% — tertiary/disabled
    val outlineVariant = Color(0xFF474656)             // ghost borders @ 15%

    // Semantic
    val error = Color(0xFFFF6E84)
    val errorContainer = Color(0xFFA70138)
    val onError = Color(0xFF490013)
    val success = Color(0xFF34D399)                    // emerald-400
    val warning = Color(0xFFFBBF24)                    // amber

    // Surface tint for ambient shadows
    val surfaceTint = Color(0xFFACA3FF)

    // Glass — used for overlays
    val glass = Color.White.copy(alpha = 0.03f)
    val glassBorder = Color.White.copy(alpha = 0.10f)
    val glassHover = Color.White.copy(alpha = 0.05f)
}

// Material3 color scheme mapped from designer tokens
private val MinimaScheme = darkColorScheme(
    primary = MinimaColors.primary,
    onPrimary = MinimaColors.onPrimary,
    primaryContainer = MinimaColors.primaryContainer,
    onPrimaryContainer = MinimaColors.onPrimaryContainer,
    secondary = MinimaColors.secondary,
    onSecondary = MinimaColors.onSecondary,
    secondaryContainer = MinimaColors.secondaryContainer,
    onSecondaryContainer = MinimaColors.onSecondaryContainer,
    tertiary = MinimaColors.tertiary,
    tertiaryContainer = MinimaColors.tertiaryContainer,
    surface = MinimaColors.surface,
    onSurface = MinimaColors.onSurface,
    surfaceVariant = MinimaColors.surfaceVariant,
    onSurfaceVariant = MinimaColors.onSurfaceVariant,
    background = MinimaColors.surface,
    onBackground = MinimaColors.onSurface,
    error = MinimaColors.error,
    errorContainer = MinimaColors.errorContainer,
    onError = MinimaColors.onError,
    outline = MinimaColors.outline,
    outlineVariant = MinimaColors.outlineVariant,
    surfaceTint = MinimaColors.surfaceTint,
)

@Composable
fun MinimaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MinimaScheme,
        content = content
    )
}
