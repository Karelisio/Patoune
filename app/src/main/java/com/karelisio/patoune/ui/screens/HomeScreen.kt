package com.karelisio.patoune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalTopBar
import com.karelisio.patoune.ui.components.EmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@Composable
fun HomeScreen(vm: MainViewModel) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val animal by vm.selected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val shared = settings?.shared == true
    val meds by remember(animal?.id) { animal?.let { c.db.medications().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val doses by remember(animal?.id) { animal?.let { c.db.doses().observeSince(it.id, System.currentTimeMillis() - 2 * 86_400_000L) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val breaths by remember(animal?.id) { animal?.let { c.db.breaths().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    var statuses by remember { mutableStateOf(emptyList<MedStatus>()) }
    LaunchedEffect(meds, doses) { statuses = meds.filter { !it.asNeeded }.map { medStatus(c, it) }.sortedBy { it.nextAt ?: Long.MAX_VALUE } }
    val scope = rememberCoroutineScope()
    val ctrl = remember { GiveController(c, scope) }

    Scaffold(topBar = { AnimalTopBar(vm) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (animal == null) {
                item { EmptyState(Icons.Rounded.Pets, "Bienvenue sur Patoune", "Ajoutez votre premier compagnon pour commencer le suivi.") }
                item { Button(onClick = { nav.navigate(Routes.AnimalEdit()) }, Modifier.fillMaxWidth()) { Text("Ajouter un animal") } }
                return@LazyColumn
            }
            item { CrisisNowButton { nav.navigate(Routes.CrisisEdit(startRecording = true)) } }
            item { animal?.let { a -> BreathSummaryCard(a, breaths) { nav.navigate(Routes.BreathCounter()) } } }
            if (statuses.isNotEmpty()) item { Text("À donner", style = MaterialTheme.typography.titleMedium) }
            items(statuses, key = { it.med.id }) { s -> DueMedCard(s, shared, onGive = { ctrl.give(s.med) }, onOpen = { nav.navigate(Routes.MedicationDetail(s.med.id)) }) }
        }
    }
    DoubleDoseDialog(ctrl, shared)
}

/** Carte de rappel : une fois la prise marquée (ici ou sur un autre téléphone), elle se replie. */
@Composable
fun DueMedCard(s: MedStatus, shared: Boolean, onGive: () -> Unit, onOpen: () -> Unit) {
    var visible by remember(s.med.id) { mutableStateOf(true) }
    LaunchedEffect(s.given) { if (s.given != null) { delay(1400); visible = false } else visible = true }
    AnimatedVisibility(visible, exit = shrinkVertically() + fadeOut()) { MedCard(s, shared, onGive, onOpen) }
}



/** Bouton « Crise en cours » : un tap lance la vidéo et horodate. */
@Composable
fun CrisisNowButton(onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = com.karelisio.patoune.ui.components.PawShapes.pill,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
    ) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Videocam, null)
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Text("Crise en cours", style = MaterialTheme.typography.titleLarge)
    }
}
