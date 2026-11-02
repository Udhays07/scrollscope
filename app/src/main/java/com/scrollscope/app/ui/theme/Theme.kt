package com.scrollscope.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

/** Iron Hoot's material: gunmetal steel with an ember accent, and a navy cast for the night lock. */
object IronColors {
    val I950 = Color(0xFF0F1114)
    val I900 = Color(0xFF15181C)
    val I800 = Color(0xFF23282E)
    val I700 = Color(0xFF353B43)
    val I600 = Color(0xFF4E5761)
    val I500 = Color(0xFF6B7580)
    val I400 = Color(0xFF8C97A3)
    val I300 = Color(0xFFA3AEBA)
    val I200 = Color(0xFFC9D2DB)
    val Wing = Color(0xFF5A636E)
    val Ember = Color(0xFFFF5A2E)
    val EmberText = Color(0xFFFF8A65)
    val EmberHot = Color(0xFFFFD08A)
    val NightSlat = Color(0xFF141A24)
    val NightGap = Color(0xFF0B0F16)
    val NightEdge = Color(0xFF1C2431)
    val Moon = Color(0xFFD5E3FF)
}

/** Iron Hoot re-skins the whole app: steel replaces amber, surfaces take a gunmetal cast. */
val IronScheme = darkColorScheme(
    primary = IronColors.I200,
    onPrimary = IronColors.I900,
    primaryContainer = IronColors.I700,
    onPrimaryContainer = Color.White,
    secondary = IronColors.I300,
    onSecondary = IronColors.I900,
    secondaryContainer = IronColors.I700,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFA6C8FF),
    onTertiary = Color(0xFF00315F),
    tertiaryContainer = Color(0xFF1A2433),
    onTertiaryContainer = Color(0xFFD5E3FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1E1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF07080A),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF07080A),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = IronColors.I800,
    onSurfaceVariant = IronColors.I300,
    outline = IronColors.I600,
    outlineVariant = IronColors.I800,
    inverseSurface = IronColors.I200,
    inverseOnSurface = IronColors.I950,
    inversePrimary = IronColors.I600,
    surfaceContainerLowest = Color(0xFF07080A),
    surfaceContainerLow = Color(0xFF0E1013),
    surfaceContainer = Color(0xFF13161A),
    surfaceContainerHigh = IronColors.I800,
    surfaceContainerHighest = Color(0xFF2B3138),
)

private val IronType = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

/** True while Iron Hoot is the app's mode; drives the avatar, status chip and theme. */
val LocalIron = staticCompositionLocalOf { false }

@Composable
fun ScrollScopeTheme(iron: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIron provides iron) {
        if (iron) MaterialTheme(colorScheme = IronScheme, typography = IronType, content = content)
        else MaterialTheme(colorScheme = HootScheme, content = content)
    }
}
