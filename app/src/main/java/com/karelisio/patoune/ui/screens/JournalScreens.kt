package com.karelisio.patoune.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.data.CrisisTypes
import com.karelisio.patoune.data.ObsCategory
import com.karelisio.patoune.data.Observation
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.newId
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalTopBar
import com.karelisio.patoune.ui.components.EmptyState
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.MultiChips
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.components.TimeDialog
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.cascade
import com.karelisio.patoune.ui.sharedKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

@Composable
fun JournalScreen(vm: MainViewModel) {
    val animal by vm.selected.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    Scaffold(
        topBar = { AnimalTopBar(vm) },
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (pager.currentPage == 0 && animal != null) ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.CrisisEdit()) }, icon = { Icon(Icons.Rounded.Sick, null) }, text = { Text("Noter une crise") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(pager.currentPage) {
                listOf("Crises", "Observations", "Galerie").forEachIndexed { i, t ->
                    Tab(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(t) })
                }
            }
            val a = animal ?: return@Column
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> CrisisList(a.id)
                    1 -> ObservationsContent(a.id, snack)
                    else -> GalleryContent(a.id)
                }
            }
        }
    }
}

@Composable
private fun CrisisList(animalId: String) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val crises by remember(animalId) { c.db.crises().observeFor(animalId) }.collectAsState(emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (crises.isEmpty()) item { EmptyState(Icons.Rounded.Sick, "Aucune crise notée", "Utilisez « Crise en cours » sur l'accueil pour filmer et horodater en un tap.") }
        items(crises, key = { it.id }) { cr ->
            val media by remember(cr.id) { c.db.media().observeAttached(cr.id) }.collectAsState(emptyList())
            SectionCard(Modifier.cascade(crises.indexOf(cr)).sharedKey("crisis-${cr.id}").clickable { nav.navigate(Routes.CrisisEdit(cr.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.Sick, PawShapes.flower, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Dates.fmtDayTime(cr.startAt), style = MaterialTheme.typography.titleMedium)
                        Text("Intensité ${cr.intensity}/5 · ${fmtDuration(cr.durationSec)}" + if (cr.types.isNotBlank()) " · ${cr.types.replace(",", ", ")}" else "",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (cr.triggers.isNotBlank()) Text("Déclencheurs : ${cr.triggers.replace(",", ", ")}", style = MaterialTheme.typography.labelLarge)
                if (media.isNotEmpty()) MediaStrip(media)
            }
        }
    }
}

fun fmtDuration(sec: Int): String = if (sec < 60) "$sec s" else "${sec / 60} min ${if (sec % 60 > 0) "${sec % 60} s" else ""}".trim()

@Composable
fun CrisisEditScreen(vm: MainViewModel, id: String?, startRecording: Boolean) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var cr by rememberSaveable(stateSaver = crisisSaver) { mutableStateOf(Crisis(id = id ?: newId(), startAt = System.currentTimeMillis(), types = "Toux")) }
    var launched by rememberSaveable { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val media by remember(cr.id) { c.db.media().observeAttached(cr.id) }.collectAsState(emptyList())
    val rescue by remember(animal?.id) { animal?.let { c.db.medications().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    LaunchedEffect(id) { if (id != null) c.db.crises().byId(id)?.let { cr = it } }
    // « Crise en cours » : enregistrement vidéo immédiat.
    LaunchedEffect(startRecording) {
        if (startRecording && !launched && animal != null) {
            launched = true
            // Horodatage immédiat : la crise existe même si l'on quitte sans compléter la fiche.
            val a = animal!!
            cr = c.store.save(cr.copy(animalId = a.id))
            c.hooks.onPush(PushType.CRISIS, "Crise en cours : ${a.name}", "Début à ${Dates.fmtTime(cr.startAt)}", cr.id)
            nav.navigate(Routes.Camera(video = true, attachType = AttachType.CRISIS.name, attachId = cr.id, autoStart = true))
        }
    }
    LaunchedEffect(media.size) {
        if (cr.durationSec == 0) media.firstOrNull { it.durationMs > 0 }?.let { cr = cr.copy(durationSec = (it.durationMs / 1000).toInt()) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val a = animal ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch { c.media.import(uri, a.id, AttachType.CRISIS, cr.id) }
    }

    Scaffold(topBar = {
        BackTopBar(if (id == null) "Crise" else "Modifier la crise") {
            if (id != null) IconButton(onClick = { scope.launch { c.store.delete(cr); nav.popBackStack() } }) { Icon(Icons.Rounded.Delete, "Supprimer") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding().sharedKey("crisis-${cr.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Dates.fmtDayTime(cr.startAt), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { pickTime = true }) { Text("Changer l'heure") }
            }
            Text("Vidéos et photos", style = MaterialTheme.typography.titleMedium)
            if (media.isNotEmpty()) MediaStrip(media)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ nav.navigate(Routes.Camera(true, AttachType.CRISIS.name, cr.id)) }, { Text("Filmer") }, leadingIcon = { Icon(Icons.Rounded.Videocam, null) })
                AssistChip({ nav.navigate(Routes.Camera(false, AttachType.CRISIS.name, cr.id)) }, { Text("Photo") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) })
                AssistChip({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, { Text("Galerie") }, leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) })
            }
            Field(if (cr.durationSec == 0) "" else cr.durationSec.toString(), { cr = cr.copy(durationSec = it.toIntOrNull() ?: 0) }, "Durée (secondes)", keyboard = KeyboardType.Number)
            Text("Intensité : ${cr.intensity}/5", style = MaterialTheme.typography.titleMedium)
            Slider(cr.intensity.toFloat(), { cr = cr.copy(intensity = it.toInt()) }, valueRange = 1f..5f, steps = 3)
            Text("Type", style = MaterialTheme.typography.titleMedium)
            MultiChips(CrisisTypes.all, cr.types) { cr = cr.copy(types = it) }
            Text("Traitement d'urgence donné", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rescue.forEach { m -> FilterChip(cr.emergencyTreatment == m.name, { cr = cr.copy(emergencyTreatment = if (cr.emergencyTreatment == m.name) "" else m.name) }, { Text(m.name) }) }
            }
            Field(cr.emergencyTreatment, { cr = cr.copy(emergencyTreatment = it) }, "Autre traitement / dose")
            Text("Déclencheurs possibles", style = MaterialTheme.typography.titleMedium)
            MultiChips(CrisisTypes.triggers, cr.triggers) { cr = cr.copy(triggers = it) }
            Field(cr.notes, { cr = cr.copy(notes = it) }, "Notes", minLines = 3)
            VetNote("En cas de détresse respiratoire, contactez immédiatement votre véto ou le véto de garde.")
            Button(onClick = {
                val a = animal ?: return@Button
                scope.launch {
                    val saved = c.store.save(cr.copy(animalId = cr.animalId.ifEmpty { a.id }))
                    if (id == null && !startRecording) c.hooks.onPush(PushType.CRISIS, "Crise enregistrée : ${a.name}", "Intensité ${saved.intensity}/5 à ${Dates.fmtTime(saved.startAt)}", saved.id)
                    c.hooks.onDataChanged()
                    nav.popBackStack()
                }
            }, Modifier.fillMaxWidth()) { Text("Enregistrer") }
        }
    }
    if (pickTime) {
        val z = Instant.ofEpochMilli(cr.startAt).atZone(Dates.zone())
        TimeDialog(z.toLocalTime(), { pickTime = false }) { t ->
            cr = cr.copy(startAt = ZonedDateTime.of(z.toLocalDate(), t, Dates.zone()).toInstant().toEpochMilli()); pickTime = false
        }
    }
}

private val crisisSaver = androidx.compose.runtime.saveable.Saver<Crisis, String>(
    save = { kotlinx.serialization.json.Json.encodeToString(Crisis.serializer(), it) },
    restore = { kotlinx.serialization.json.Json.decodeFromString(Crisis.serializer(), it) },
)

/** Saisie rapide d'observations : un tap sur une valeur l'enregistre immédiatement. */
@Composable
private fun ObservationsContent(animalId: String, snack: SnackbarHostState) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val list by remember(animalId) { c.db.observations().observeFor(animalId) }.collectAsState(emptyList())
    var noteFor by remember { mutableStateOf<Observation?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ObsCategory.entries) { cat ->
            SectionCard {
                Text(cat.label, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cat.options.forEach { opt ->
                        AssistChip(onClick = {
                            scope.launch {
                                val o = c.store.save(Observation(animalId = animalId, at = System.currentTimeMillis(), category = cat, value = opt))
                                val r = snack.showSnackbar("${cat.label} : $opt", actionLabel = "Ajouter une note", withDismissAction = true)
                                if (r == SnackbarResult.ActionPerformed) noteFor = o
                            }
                        }, label = { Text(opt) })
                    }
                }
            }
        }
        item { Text("Historique", style = MaterialTheme.typography.titleMedium) }
        items(list.take(100), key = { it.id }) { o ->
            ListItem(
                headlineContent = { Text("${o.category.label} : ${o.value}") },
                supportingContent = { Text(Dates.fmtDayTime(o.at) + if (o.note.isNotBlank()) " · ${o.note}" else "") },
                trailingContent = { IconButton(onClick = { scope.launch { c.store.delete(o) } }) { Icon(Icons.Rounded.Delete, "Supprimer") } },
            )
        }
    }
    noteFor?.let { o ->
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text("Note") },
            text = { Field(text, { text = it }, "Note", minLines = 2) },
            confirmButton = { TextButton(onClick = { scope.launch { c.store.save(o.copy(note = text)) }; noteFor = null }) { Text("OK") } },
        )
    }
}

