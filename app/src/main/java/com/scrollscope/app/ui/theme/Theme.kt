package com.scrollscope.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Material 3 dark scheme generated around Hoot's amber. */
val HootScheme = darkColorScheme(
    primary = Color(0xFFFFB68A),
    onPrimary = Color(0xFF522300),
    primaryContainer = Color(0xFF5D3A22),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE5BFA9),
    onSecondary = Color(0xFF432B1D),
    secondaryContainer = Color(0xFF5D4132),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFFA6C8FF),
    onTertiary = Color(0xFF00315F),
    tertiaryContainer = Color(0xFF23364F),
    onTertiaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A120E),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A120E),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF52443C),
    inverseSurface = Color(0xFFF0DFD7),
    inverseOnSurface = Color(0xFF392E29),
    inversePrimary = Color(0xFF8F4C1E),
    surfaceContainerLowest = Color(0xFF140C09),
    surfaceContainerLow = Color(0xFF231A15),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322823),
    surfaceContainerHighest = Color(0xFF3D332D),
)

object HootColors {
    val OwlBackdrop = Color(0xFF2E1D13)
    val Good = Color(0xFF9CD67D)
    val GoodContainer = Color(0xFF1F3517)
    val Amber = Color(0xFFFFCF70)
    val OnAmber = Color(0xFF3F2E00)
    val BarMuted = Color(0xFF6E4A33)
    val BadContainer = Color(0xFF5C1E1A)
}

@Composable
fun ScrollScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HootScheme, content = content)
}
