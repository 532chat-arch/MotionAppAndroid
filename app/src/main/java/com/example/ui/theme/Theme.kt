package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MotionStudioColorScheme = darkColorScheme(
    primary = StudioPrimaryBright,
    onPrimary = StudioOnPrimary,
    primaryContainer = StudioPrimary,
    onPrimaryContainer = StudioPrimaryContainer,
    inversePrimary = Color(0xFF0061A3),
    secondary = StudioSecondaryBright,
    onSecondary = Color(0xFF2B2C57),
    secondaryContainer = StudioSecondaryContainer,
    onSecondaryContainer = Color(0xFFB4B4E8),
    tertiary = StudioCyan,
    onTertiary = Color(0xFF003257),
    tertiaryContainer = Color(0xFF509BE4),
    onTertiaryContainer = Color(0xFF003155),
    background = StudioSurface,
    onBackground = Color(0xFFE5E2E1),
    surface = StudioSurface,
    onSurface = Color(0xFFE5E2E1),
    surfaceVariant = StudioSurfaceHigh,
    onSurfaceVariant = Color(0xFFC0C7D3),
    surfaceTint = StudioPrimaryBright,
    inverseSurface = Color(0xFFE5E2E1),
    inverseOnSurface = Color(0xFF313030),
    surfaceContainerLowest = StudioSurfaceLowest,
    surfaceContainerLow = StudioBg,
    surfaceContainer = Color(0xFF20201F),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = StudioSurfaceHigh,
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFF404751),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Soft architectural roundedness (4dp panels/inputs, 6dp floating containers)
val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun MotionStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotionStudioColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content,
    )
}
