package com.scrollscope.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** True-black Material 3 scheme; amber is kept for Hoot and highlights only. */
val HootScheme = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF522300),
    primaryContainer = Color(0xFF33231A),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE5BFA9),
    onSecondary = Color(0xFF432B1D),
    secondaryContainer = Color(0xFF2A2A2A),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFFA6C8FF),
    onTertiary = Color(0xFF00315F),
    tertiaryContainer = Color(0xFF1A2433),
    onTertiaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2E2E2E),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF5C5C5C),
    outlineVariant = Color(0xFF2E2E2E),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = Color(0xFF8F4C1E),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
)

object HootColors {
    val OwlBackdrop = Color(0xFF161616)
    val Good = Color(0xFF9CD67D)
    val GoodContainer = Color(0xFF1F3517)
    val Amber = Color(0xFFFFCF70)
    val OnAmber = Color(0xFF3F2E00)
    val BarMuted = Color(0xFF3A3A3A)
    val BarOver = Color(0xFF6B2A26)
    val BadContainer = Color(0xFF5C1E1A)
    val Heat = listOf(Color(0xFF1C1C1C), Color(0xFF3A2414), Color(0xFF7A4524), Color(0xFFC96A33))
}

@Composable
fun ScrollScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HootScheme, content = content)
}
