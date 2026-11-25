package com.karelisio.patoune.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders
import com.karelisio.patoune.ui.theme.DarkFallback
import com.karelisio.patoune.ui.theme.LightFallback

/**
 * Thème des widgets : couleurs dynamiques Material You (Android 12+, tirées du fond d'écran),
 * palette pêche / sauge de l'appli en secours, mode sombre automatique.
 */
@Composable
fun PatouneGlanceTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders
    else ColorProviders(light = LightFallback, dark = DarkFallback)
    GlanceTheme(colors = colors, content = content)
}
