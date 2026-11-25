package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.patoune.container
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AppPermission
import com.karelisio.patoune.ui.components.PawIcon
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.Perms
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.rememberPermissionRequester
import com.karelisio.patoune.ui.components.rememberReduceMotion
import com.karelisio.patoune.ui.components.rememberResumeTick
import kotlinx.coroutines.launch

enum class StartMode { SOLO, CREATE, JOIN }

/** Onboarding : présentation, premier animal, mode, puis une page par autorisation. */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val perms = AppPermission.entries
    var mode by remember { mutableStateOf(StartMode.SOLO) }
    // Rejoindre un foyer : pas de fiche animal, les animaux arrivent du foyer après l'appairage.
    val withAnimal = mode != StartMode.JOIN
    val animalPages = if (withAnimal) 1 else 0
    val pages = 2 + animalPages + perms.size
    val pager = rememberPagerState { pages }
    val next: () -> Unit = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
    val finish: () -> Unit = {
        scope.launch {
            c.settings.setOnboardingDone()
            nav.navigate(Routes.Home) { popUpTo(Routes.Onboarding) { inclusive = true } }
            if (mode != StartMode.SOLO) nav.navigate(Routes.Household)
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = !(withAnimal && pager.currentPage == 2)) { page ->
            when {
                page == 0 -> IntroPage(next)
                page == 1 -> ModePage(mode, { mode = it }, next)
                withAnimal && page == 2 -> Column(Modifier.fillMaxSize()) {
                    Text(stringResource(R.string.onboarding_animal), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(24.dp, 16.dp, 24.dp, 0.dp))
                    AnimalEditScreen(vm, null, onSaved = next, embedded = true)
                }
                else -> PermissionPage(perms[page - 2 - animalPages], last = page == pages - 1, onNext = { if (page == pages - 1) finish() else next() })
            }
        }
        val pageLabel = stringResource(R.string.a11y_page, pager.currentPage + 1, pages)
        Row(Modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics { contentDescription = pageLabel }, horizontalArrangement = Arrangement.Center) {
            repeat(pages) { i ->
                val active = i == pager.currentPage
                Box(Modifier.padding(3.dp).height(8.dp).width(if (active) 24.dp else 8.dp).clip(PawShapes.pill)
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun IntroPage(onNext: () -> Unit) {
    val reduce = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "intro")
    val breathe by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "b")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "s")
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // Forme animée décorative : ignorée par l'accessibilité.
        Box(Modifier.size(200.dp).clearAndSetSemantics {}.graphicsLayer { if (!reduce) { scaleX = breathe; scaleY = breathe; rotationZ = spin } }.clip(PawShapes.cookie)
            .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            PawIcon(MaterialTheme.colorScheme.onPrimaryContainer, Modifier.graphicsLayer { rotationZ = if (reduce) 0f else -spin }, size = 110.dp)
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium)
        Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_intro_local), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VetNote()
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNext, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_start)) }
    }
}

@Composable
private fun ModePage(mode: StartMode, onMode: (StartMode) -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onboarding_mode_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_mode_body), style = MaterialTheme.typography.bodyMedium)
        ModeCard(Icons.Rounded.Person, stringResource(R.string.settings_solo), stringResource(R.string.onboarding_solo_body), mode == StartMode.SOLO) { onMode(StartMode.SOLO) }
        ModeCard(Icons.Rounded.GroupAdd, stringResource(R.string.household_create_title), stringResource(R.string.onboarding_create_body), mode == StartMode.CREATE) { onMode(StartMode.CREATE) }
        ModeCard(Icons.Rounded.Group, stringResource(R.string.household_join_title), stringResource(R.string.onboarding_join_body), mode == StartMode.JOIN) { onMode(StartMode.JOIN) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onNext, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_continue)) }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(icon, if (selected) PawShapes.sunny else PawShapes.pad, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PermissionPage(p: AppPermission, last: Boolean, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableStateOf(0) }
    val granted = remember(tick, refresh) { Perms.granted(ctx, p) }
    val request = rememberPermissionRequester { refresh++ }
    val reduce = rememberReduceMotion()
    val t = rememberInfiniteTransition(label = "perm")
    val wobble by t.animateFloat(-8f, 8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "w")
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AnimatedContent(granted, transitionSpec = { scaleIn(spring(Spring.DampingRatioMediumBouncy)) togetherWith scaleOut() }, label = "check",
            modifier = Modifier.clearAndSetSemantics {}) { ok ->
            if (ok) ShapeBadge(Icons.Rounded.Check, PawShapes.sunny, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, 140.dp)
            else ShapeBadge(p.icon, PawShapes.cookie, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, 140.dp,
                Modifier.graphicsLayer { if (!reduce) rotationZ = wobble })
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(p.titleRes), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        if (granted) Text(stringResource(R.string.perm_granted), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(p.whyRes), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        if (!granted) Button(onClick = { request(p) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.perm_allow)) }
        else Button(onClick = onNext, Modifier.fillMaxWidth()) { Text(stringResource(if (last) R.string.onboarding_finish else R.string.action_continue)) }
        if (!granted) TextButton(onClick = onNext) { Text(stringResource(if (last) R.string.onboarding_finish_without else R.string.onboarding_later)) }

    }
}
