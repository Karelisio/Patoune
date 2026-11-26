package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.LocalHospital
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
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.Due
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
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
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(meds, doses, refresh) { statuses = meds.filter { !it.asNeeded }.map { medStatus(c, it) }.sortedBy { it.nextAt ?: Long.MAX_VALUE } }
    // Une prise toute récente garde sa carte « À donner » le temps du repli ; ensuite, recalcul pour la passer « À venir ».
    LaunchedEffect(statuses) {
        val now = System.currentTimeMillis()
        val until = statuses.mapNotNull { s -> s.given?.takenAt?.plus(Due.RECENT_MS)?.takeIf { it > now } }.minOrNull() ?: return@LaunchedEffect
        delay(until - now + 1_000)
        refresh++
    }
    val due = statuses.filter { it.due }
    val upcoming = statuses.filter { !it.due }
    val scope = rememberCoroutineScope()
    val ctrl = remember { GiveController(c, scope) }

    Scaffold(topBar = { AnimalTopBar(vm) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (animal == null) {
                item { EmptyState(Icons.Rounded.Pets, stringResource(R.string.home_welcome), stringResource(R.string.home_welcome_body)) }
                item { Button(onClick = { nav.navigate(Routes.AnimalEdit()) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.animal_add)) } }
                return@LazyColumn
            }
            item { CrisisNowButton { nav.navigate(Routes.CrisisEdit(startRecording = true)) } }
            item {
                androidx.compose.material3.OutlinedButton(onClick = { nav.navigate(Routes.Emergency) }, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.LocalHospital, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.emergency_title))
                }
            }
            item { animal?.let { a -> BreathSummaryCard(a, breaths) { nav.navigate(Routes.BreathCounter()) } } }
            if (due.isNotEmpty()) item { Text(stringResource(R.string.home_due), style = MaterialTheme.typography.titleMedium) }
            items(due, key = { it.med.id }) { s -> DueMedCard(s, shared, onGive = { ctrl.give(s.med) }, onOpen = { nav.navigate(Routes.MedicationDetail(s.med.id)) }) }
            if (upcoming.isNotEmpty()) item { Text(stringResource(R.string.home_upcoming), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
            items(upcoming, key = { "upcoming-" + it.med.id }) { s -> UpcomingMedRow(s) { nav.navigate(Routes.MedicationDetail(s.med.id)) } }
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


/** Ligne compacte d'un traitement pas encore à donner : nom et prochaine échéance ; un tap ouvre la fiche. */
@Composable
fun UpcomingMedRow(s: MedStatus, onOpen: () -> Unit) {
    val m = s.med
    androidx.compose.material3.ListItem(
        headlineContent = { Text(m.name) },
        supportingContent = {
            Text(s.nextAt?.let { stringResource(R.string.med_next_on, Dates.fmtDay(it), Dates.fmtTime(it)) } ?: stringResource(R.string.med_finished))
        },
        leadingContent = { androidx.compose.material3.Icon(m.type.icon(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onOpen),
    )
}

/** Bouton « Crise en cours » : un tap lance la vidéo et horodate. */
@Composable
fun CrisisNowButton(onClick: () -> Unit) {
    val description = stringResource(R.string.a11y_crisis_now)
    androidx.compose.material3.Button(
        onClick = onClick,
        // Le bouton fusionne ses enfants (rôle bouton) : le texte porte un libellé explicite pour TalkBack.
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = com.karelisio.patoune.ui.components.PawShapes.pill,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
    ) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Videocam, null)
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.crisis_now), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = description })


    }
}
