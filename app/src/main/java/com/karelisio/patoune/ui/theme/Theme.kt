package com.karelisio.patoune.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Fallback doux : pêche et vert sauge.
private val Peach = Color(0xFF9A4A2B)
private val PeachLight = Color(0xFFFFDBCF)
private val Sage = Color(0xFF55634F)
private val SageLight = Color(0xFFD8E8CE)

internal val LightFallback: ColorScheme = lightColorScheme(
    primary = Peach, onPrimary = Color.White, primaryContainer = PeachLight, onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Sage, onSecondary = Color.White, secondaryContainer = SageLight, onSecondaryContainer = Color(0xFF131F10),
    tertiary = Color(0xFF6B5E2F), tertiaryContainer = Color(0xFFF5E2A7), onTertiaryContainer = Color(0xFF231B00),
    background = Color(0xFFFFF8F5), onBackground = Color(0xFF231A16),
    surface = Color(0xFFFFF8F5), onSurface = Color(0xFF231A16),
    surfaceVariant = Color(0xFFF5DED6), onSurfaceVariant = Color(0xFF53433E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4), surfaceContainerHigh = Color(0xFFF6E4DE), surfaceContainerHighest = Color(0xFFF0DFD8),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736D), outlineVariant = Color(0xFFD8C2BA),
)

internal val DarkFallback: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59A), onPrimary = Color(0xFF5B1A03), primaryContainer = Color(0xFF7B3317), onPrimaryContainer = PeachLight,
    secondary = Color(0xFFBCCCB3), onSecondary = Color(0xFF273423), secondaryContainer = Color(0xFF3D4B38), onSecondaryContainer = SageLight,
    tertiary = Color(0xFFD8C68D), tertiaryContainer = Color(0xFF524619), onTertiaryContainer = Color(0xFFF5E2A7),
    background = Color(0xFF1A110E), onBackground = Color(0xFFF1DFD9),
    surface = Color(0xFF1A110E), onSurface = Color(0xFFF1DFD9),
    surfaceVariant = Color(0xFF53433E), onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFF140C09), surfaceContainerLow = Color(0xFF231A16),
    surfaceContainer = Color(0xFF271E1A), surfaceContainerHigh = Color(0xFF322824), surfaceContainerHighest = Color(0xFF3D332E),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08D86), outlineVariant = Color(0xFF53433E),
)

private val base = Typography()
val PatouneTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Style du gros chiffre (compteur, fréquence). */
val BigNumber = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp)

/** [dynamicColor] : couleurs du fond d'écran (Android 12+) ; false force la palette pêche/sauge (captures d'écran). */
@Composable
fun PatouneTheme(dark: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = PatouneTypography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
