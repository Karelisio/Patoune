package com.karelisio.patoune.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.container
import com.karelisio.patoune.ui.theme.PatouneTheme

/** Outils communs aux tests d'interface : conteneur de test, textes des ressources, hôte d'écran. */
object Ui {
    val ctx: Context get() = ApplicationProvider.getApplicationContext()
    val c: AppContainer get() = ctx.container
    fun str(@StringRes id: Int, vararg args: Any): String = ctx.getString(id, *args)
    fun plural(id: Int, n: Int, vararg args: Any): String = ctx.resources.getQuantityString(id, n, *args)
}

/**
 * Affiche un écran comme dans l'appli : thème, ViewModel principal et navigation (graphe à un seul écran).
 * [dark] null = thème du système ; [dynamicColor] false = palette de l'appli (captures reproductibles).
 */
fun ComposeContentTestRule.setScreen(dark: Boolean? = null, dynamicColor: Boolean = true, screen: @Composable (MainViewModel) -> Unit) {
    setContent {
        val content: @Composable () -> Unit = {
            val vm = appViewModel { MainViewModel(it) }
            val nav = rememberNavController()
            CompositionLocalProvider(LocalNav provides nav) {
                NavHost(nav, startDestination = "ecran") { composable("ecran") { screen(vm) } }
            }
        }
        if (dark == null) PatouneTheme(dynamicColor = dynamicColor, content = content)
        else PatouneTheme(dark = dark, dynamicColor = dynamicColor, content = content)
    }
}
