package com.karelisio.patoune.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.ui.components.PawTrail
import com.karelisio.patoune.update.UpdateSheet
import androidx.compose.ui.platform.LocalContext
import com.karelisio.patoune.container

@Composable
fun PatouneRoot(launchAction: String?, onActionConsumed: () -> Unit, onReady: () -> Unit) {
    val vm = appViewModel { MainViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val release by vm.release.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var splashTrail by remember { mutableStateOf(true) }
    val ctx = LocalContext.current

    val s = settings ?: return
    LaunchedEffect(Unit) { onReady(); vm.checkUpdate() }

    Box(Modifier.fillMaxSize()) {
        AppNav(vm, s, launchAction, onActionConsumed)
        // Animation signature au démarrage et à chaque changement d'animal.
        AnimatedVisibility(splashTrail, exit = fadeOut()) {
            PawTrail(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), key = "splash") { splashTrail = false }
        }
        var lastAnimal by remember { mutableStateOf(selected?.id) }
        var trailKey by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(selected?.id) {
            if (lastAnimal != null && selected?.id != lastAnimal) trailKey = selected?.id
            lastAnimal = selected?.id
        }
        trailKey?.let { k -> PawTrail(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), key = k) { trailKey = null } }
    }
    release?.let { r -> UpdateSheet(r, ctx.container.updater) { vm.release.value = null } }
}
