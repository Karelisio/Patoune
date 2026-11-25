package com.karelisio.patoune.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.karelisio.patoune.MainActivity
import com.karelisio.patoune.data.AppSettings
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.media.CameraScreen
import com.karelisio.patoune.ui.screens.CrisisEditScreen
import com.karelisio.patoune.ui.screens.JournalScreen
import com.karelisio.patoune.ui.screens.MediaViewerScreen
import com.karelisio.patoune.ui.screens.AnimalEditScreen
import com.karelisio.patoune.ui.screens.BackupScreen
import com.karelisio.patoune.ui.screens.OnboardingScreen
import com.karelisio.patoune.ui.screens.PermissionsScreen
import com.karelisio.patoune.ui.screens.EmergencyScreen
import com.karelisio.patoune.ui.screens.HealthScreen
import com.karelisio.patoune.ui.screens.BreathCounterScreen
import com.karelisio.patoune.ui.screens.BreathScreen
import com.karelisio.patoune.ui.screens.HomeScreen
import com.karelisio.patoune.ui.screens.MedicationDetailScreen
import com.karelisio.patoune.ui.screens.MedicationEditScreen
import com.karelisio.patoune.ui.screens.TreatmentsScreen
import com.karelisio.patoune.ui.screens.SettingsScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.Home, Routes.Home::class, "Accueil", Icons.Rounded.Home),
    Tab(Routes.Treatments, Routes.Treatments::class, "Soins", Icons.Rounded.Medication),
    Tab(Routes.Breath, Routes.Breath::class, "Souffle", Icons.Rounded.Air),
    Tab(Routes.Journal, Routes.Journal::class, "Journal", Icons.Rounded.Book),
    Tab(Routes.Health, Routes.Health::class, "Carnet", Icons.Rounded.MonitorHeart),
)

/** Accès à la navigation depuis n'importe quel écran. */
val LocalNav = compositionLocalOf<NavHostController> { error("nav") }

@Composable
fun AppNav(vm: MainViewModel, settings: AppSettings, launchAction: String?, onActionConsumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = tabs.any { t -> dest?.hasRoute(t.type) == true }

    LaunchedEffect(launchAction) {
        when (launchAction) {
            MainActivity.ACTION_BREATH -> nav.navigate(Routes.BreathCounter())
            MainActivity.ACTION_EMERGENCY -> nav.navigate(Routes.Emergency)
            MainActivity.ACTION_CRISIS -> nav.navigate(Routes.CrisisEdit(startRecording = true))
        }
        if (launchAction != null) onActionConsumed()
    }

    CompositionLocalProvider(LocalNav provides nav) {
        Scaffold(
            bottomBar = {
                if (showBar) NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = dest?.hasRoute(t.type) == true,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(Routes.Home) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedScope provides this) {
                    NavHost(
                        navController = nav,
                        startDestination = if (settings.onboardingDone) Routes.Home else Routes.Onboarding,
                        modifier = Modifier.padding(bottom = if (showBar) pad.calculateBottomPadding() else androidx.compose.ui.unit.Dp(0f)),
                        enterTransition = { fadeIn(tween(220)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320)) { it / 8 } },
                        exitTransition = { fadeOut(tween(160)) },
                        popEnterTransition = { fadeIn(tween(220)) },
                        popExitTransition = { fadeOut(tween(160)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320)) { it / 8 } },
                    ) {
                        graph(vm, settings)
                    }
                }
            }
        }
    }
}

private fun NavGraphBuilder.graph(vm: MainViewModel, settings: AppSettings) {
    composable<Routes.Home> { WithAnim(this) { HomeScreen(vm) } }
    composable<Routes.Settings> { SettingsScreen(vm) }
    composable<Routes.Onboarding> { OnboardingScreen(vm) }
    composable<Routes.Permissions> { PermissionsScreen() }
    composable<Routes.Backup> { BackupScreen() }
    composable<Routes.Household> { SettingsScreen(vm) }
    composable<Routes.Report> { SettingsScreen(vm) }
    composable<Routes.AnimalEdit> { AnimalEditScreen(vm, it.toRoute<Routes.AnimalEdit>().id) }
    composable<Routes.Treatments> { WithAnim(this) { TreatmentsScreen(vm) } }
    composable<Routes.MedicationEdit> { MedicationEditScreen(vm, it.toRoute<Routes.MedicationEdit>().id) }
    composable<Routes.MedicationDetail> { WithAnim(this) { MedicationDetailScreen(vm, it.toRoute<Routes.MedicationDetail>().id) } }
    composable<Routes.Breath> { BreathScreen(vm) }
    composable<Routes.BreathCounter> { BreathCounterScreen(vm, it.toRoute<Routes.BreathCounter>().night) }
    composable<Routes.Journal> { WithAnim(this) { JournalScreen(vm) } }
    composable<Routes.CrisisEdit> { val r = it.toRoute<Routes.CrisisEdit>(); WithAnim(this) { CrisisEditScreen(vm, r.id, r.startRecording) } }
    composable<Routes.MediaViewer> { WithAnim(this) { MediaViewerScreen(it.toRoute<Routes.MediaViewer>().id) } }
    composable<Routes.Camera> {
        val r = it.toRoute<Routes.Camera>()
        val nav = LocalNav.current
        val animal = vm.selected.collectAsStateWithLifecycle().value
        if (animal != null) CameraScreen(r.video, animal.id, AttachType.valueOf(r.attachType), r.attachId, r.autoStart) { nav.popBackStack() }
    }
    composable<Routes.Health> { WithAnim(this) { HealthScreen(vm) } }
    composable<Routes.Emergency> { EmergencyScreen(vm) }
}
