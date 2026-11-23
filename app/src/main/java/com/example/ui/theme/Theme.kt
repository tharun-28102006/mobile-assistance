package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val AuraDarkColorScheme = darkColorScheme(
    primary = AuraCyan,
    onPrimary = Color(0xFF041A24),
    primaryContainer = AuraCyanDim,
    onPrimaryContainer = Color(0xFFCFF8FF),
    secondary = AuraCobalt,
    onSecondary = Color(0xFF061630),
    secondaryContainer = AuraCobaltDim,
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = AuraMint,
    onTertiary = Color(0xFF042219),
    tertiaryContainer = AuraMintDim,
    onTertiaryContainer = Color(0xFFC5FDE7),
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = AuraCoral,
    onError = Color(0xFF2B060D),
    errorContainer = AuraCoralDim,
    onErrorContainer = Color(0xFFFFD9DE)
)

private val AuraLightColorScheme = lightColorScheme(
    primary = SapphirePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF00244D),
    secondary = TealSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1F4F8),
    onSecondaryContainer = Color(0xFF003238),
    tertiary = EmeraldTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF023E2B),
    background = GlacialCanvas,
    onBackground = TextPrimaryLight,
    surface = GlacialSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = GlacialSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = GlacialOutline,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val AuraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AuraAssistTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AuraDarkColorScheme else AuraLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AuraShapes,
        content = content
    )
}
