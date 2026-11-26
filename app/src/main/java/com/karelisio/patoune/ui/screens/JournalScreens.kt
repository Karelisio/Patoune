package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.ui.stableLabel
import com.karelisio.patoune.ui.Labels
import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import android.content.Context
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalResources
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
import com.karelisio.patoune.ui.components.DateTimeField
import com.karelisio.patoune.ui.components.ChoiceChips
import com.karelisio.patoune.data.EntryOps
import com.karelisio.patoune.domain.Backdate
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.cascade
import com.karelisio.patoune.ui.sharedKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

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
                onClick = { nav.navigate(Routes.CrisisEdit()) }, icon = { Icon(Icons.Rounded.Sick, null) }, text = { Text(stringResource(R.string.journal_fab)) })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(pager.currentPage) {
                listOf(R.string.journal_tab_crises, R.string.journal_tab_observations, R.string.journal_tab_gallery).forEachIndexed { i, t ->
                    Tab(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(stringResource(t)) })
                }
            }
            val a = animal ?: return@Column
            HorizontalPager(pager, Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) { page ->
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
    val ctx = LocalContext.current
    val c = ctx.container
    val shared = c.sharedFlag
    val nav = LocalNav.current
    // null = pas encore chargé : on n'affiche pas l'état vide le temps de la lecture (sinon un trou reste en tête de liste).
    val loaded by remember(animalId) { c.db.crises().observeFor(animalId) }.collectAsState(null)
    val crises = loaded.orEmpty()
    val breaths by remember(animalId) { c.db.breaths().observeFor(animalId) }.collectAsState(emptyList())
    val air by remember { c.db.air().observeAll() }.collectAsState(emptyList())
    if (loaded == null) return
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (crises.isNotEmpty()) item(key = "insights") { InsightsCard(crises, breaths, air) }
        if (crises.isEmpty()) item(key = "empty") { EmptyState(Icons.Rounded.Sick, stringResource(R.string.journal_empty), stringResource(R.string.journal_empty_body)) }
        items(crises, key = { it.id }) { cr ->
            val media by remember(cr.id) { c.db.media().observeAttached(cr.id) }.collectAsState(emptyList())
            SectionCard(Modifier.cascade(crises.indexOf(cr)).sharedKey("crisis-${cr.id}").clickable { nav.navigate(Routes.CrisisEdit(cr.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(Icons.Rounded.Sick, PawShapes.flower, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Dates.fmtDayTime(cr.startAt), style = MaterialTheme.typography.titleMedium)
                        val head = stringResource(R.string.crisis_summary, cr.intensity, fmtDuration(ctx, cr.durationSec))
                        Text(if (cr.types.isNotBlank()) stringResource(R.string.pair_dot, head, Labels.csv(ctx, CrisisTypes.labels, cr.types)) else head,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (shared && cr.createdBy.isNotBlank()) Text(stringResource(R.string.crisis_noted_by, cr.createdBy), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cr.triggers.isNotBlank()) Text(stringResource(R.string.crisis_triggers_list, Labels.csv(ctx, CrisisTypes.labels, cr.triggers)), style = MaterialTheme.typography.labelLarge)
                if (media.isNotEmpty()) MediaStrip(media)
            }
        }
    }
}

fun fmtDuration(ctx: Context, sec: Int): String = when {
    sec < 60 -> ctx.getString(R.string.duration_sec, sec)
    sec % 60 == 0 -> ctx.getString(R.string.duration_min, sec / 60)
    else -> ctx.getString(R.string.duration_min_sec, sec / 60, sec % 60)
}

@Composable
fun CrisisEditScreen(vm: MainViewModel, id: String?, startRecording: Boolean) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var cr by rememberSaveable(stateSaver = crisisSaver) { mutableStateOf(Crisis(id = id ?: newId(), startAt = System.currentTimeMillis(), types = "Toux")) }
    var launched by rememberSaveable { mutableStateOf(false) }
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
            c.hooks.onPush(PushType.CRISIS, res.getString(R.string.push_crisis_now, a.name), res.getString(R.string.push_crisis_start, Dates.fmtTime(cr.startAt)), cr.id)
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
        BackTopBar(stringResource(if (id == null) R.string.crisis_title else R.string.crisis_edit_title)) {
            if (id != null) IconButton(onClick = { scope.launch { c.store.delete(cr); nav.popBackStack() } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
        }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding().sharedKey("crisis-${cr.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Date et heure modifiables : une crise peut être notée après coup.
            DateTimeField(cr.startAt, { cr = cr.copy(startAt = it) }, stringResource(R.string.crisis_start_field))
            Text(stringResource(R.string.crisis_media), style = MaterialTheme.typography.titleMedium)
            if (media.isNotEmpty()) MediaStrip(media)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ nav.navigate(Routes.Camera(true, AttachType.CRISIS.name, cr.id)) }, { Text(stringResource(R.string.crisis_film)) }, leadingIcon = { Icon(Icons.Rounded.Videocam, null) })
                AssistChip({ nav.navigate(Routes.Camera(false, AttachType.CRISIS.name, cr.id)) }, { Text(stringResource(R.string.media_photo)) }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) })
                AssistChip({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, { Text(stringResource(R.string.journal_tab_gallery)) }, leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, null) })
            }
            Field(if (cr.durationSec == 0) "" else cr.durationSec.toString(), { cr = cr.copy(durationSec = it.toIntOrNull() ?: 0) }, stringResource(R.string.crisis_duration_field), keyboard = KeyboardType.Number)
            Text(stringResource(R.string.crisis_intensity, cr.intensity), style = MaterialTheme.typography.titleMedium)
            Slider(cr.intensity.toFloat(), { cr = cr.copy(intensity = it.toInt()) }, valueRange = 1f..5f, steps = 3)
            Text(stringResource(R.string.crisis_type), style = MaterialTheme.typography.titleMedium)
            // Valeurs stockées : clés françaises stables ; seul le libellé est traduit.
            MultiChips(CrisisTypes.all, cr.types, { stableLabel(CrisisTypes.labels, it) }) { cr = cr.copy(types = it) }
            Text(stringResource(R.string.crisis_rescue), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rescue.forEach { m -> FilterChip(cr.emergencyTreatment == m.name, { cr = cr.copy(emergencyTreatment = if (cr.emergencyTreatment == m.name) "" else m.name) }, { Text(m.name) }) }
            }
            Field(cr.emergencyTreatment, { cr = cr.copy(emergencyTreatment = it) }, stringResource(R.string.crisis_rescue_other))
            Text(stringResource(R.string.crisis_triggers), style = MaterialTheme.typography.titleMedium)
            MultiChips(CrisisTypes.triggers, cr.triggers, { stableLabel(CrisisTypes.labels, it) }) { cr = cr.copy(triggers = it) }
            Field(cr.notes, { cr = cr.copy(notes = it) }, stringResource(R.string.field_notes), minLines = 3)
            VetNote(stringResource(R.string.crisis_vet_note))
            Button(enabled = !Backdate.isFuture(cr.startAt, System.currentTimeMillis()), onClick = {
                val a = animal ?: return@Button
                scope.launch {
                    val saved = c.store.save(cr.copy(animalId = cr.animalId.ifEmpty { a.id }))
                    if (id == null && !startRecording) c.hooks.onPush(PushType.CRISIS, res.getString(R.string.push_crisis_saved, a.name),
                        res.getString(R.string.push_crisis_saved_body, saved.intensity, Dates.fmtTime(saved.startAt)), saved.id)
                    c.hooks.onDataChanged()
                    nav.popBackStack()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
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
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    val list by remember(animalId) { c.db.observations().observeFor(animalId) }.collectAsState(emptyList())
    var noteFor by remember { mutableStateOf<Observation?>(null) }
    /** Observation à corriger, ou nouvelle (id vide) ajoutée après coup. */
    var editing by remember { mutableStateOf<Observation?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ObsCategory.entries) { cat ->
            SectionCard {
                Text(stringResource(cat.labelRes), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cat.options.forEach { opt ->
                        AssistChip(onClick = {
                            scope.launch {
                                val o = c.store.save(Observation(animalId = animalId, at = System.currentTimeMillis(), category = cat, value = opt))
                                val r = snack.showSnackbar(res.getString(R.string.obs_line, res.getString(cat.labelRes), Labels.obs(ctx, opt)),
                                    actionLabel = res.getString(R.string.obs_add_note), withDismissAction = true)
                                if (r == SnackbarResult.ActionPerformed) noteFor = o
                            }
                        }, label = { Text(stableLabel(ObsCategory.optionLabels, opt)) })
                    }
                }
            }
        }
        item { Text(stringResource(R.string.obs_history), style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedButton(onClick = { editing = Observation(animalId = animalId, at = System.currentTimeMillis()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.obs_add_past))
            }
        }
        items(list.take(100), key = { it.id }) { o ->
            val line = stringResource(R.string.obs_line, stringResource(o.category.labelRes), stableLabel(ObsCategory.optionLabels, o.value))
            ListItem(
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.obs_edit_title)) { editing = o },
                headlineContent = { Text(line) },
                supportingContent = {
                    Text(listOfNotNull(Dates.fmtDayTime(o.at), o.note.takeIf { it.isNotBlank() }, o.createdBy.takeIf { c.sharedFlag && it.isNotBlank() })
                        .joinToString(" · "))
                },
                trailingContent = { IconButton(onClick = { scope.launch { c.store.delete(o) } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete_named, line)) } },
            )
        }
    }
    noteFor?.let { o ->
        var text by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteFor = null },
            title = { Text(stringResource(R.string.obs_note)) },
            text = { Field(text, { text = it }, stringResource(R.string.obs_note), minLines = 2) },
            confirmButton = { TextButton(onClick = { scope.launch { c.store.save(o.copy(note = text)) }; noteFor = null }) { Text(stringResource(R.string.action_ok)) } },

        )
    }
    editing?.let { ObservationSheet(it) { editing = null } }
}

/** Ajout ([base] sans id) ou correction d'une observation : date, catégorie, valeur (clé française stockée), note. */
@Composable
private fun ObservationSheet(base: Observation, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val isNew = base.id.isEmpty()
    var at by remember { mutableLongStateOf(base.at) }
    var category by remember { mutableStateOf(base.category) }
    var value by remember { mutableStateOf(base.value.takeIf { it in base.category.options }) }
    var note by remember { mutableStateOf(base.note) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (isNew) R.string.obs_add_past else R.string.obs_edit_title), style = MaterialTheme.typography.titleLarge)
            DateTimeField(at, { at = it }, stringResource(R.string.field_date))
            Text(stringResource(R.string.obs_category), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(ObsCategory.entries, category, { stringResource(it.labelRes) }) {
                // Les options changent avec la catégorie : la valeur est à rechoisir si elle n'existe plus.
                if (it != category) { category = it; if (value !in it.options) value = null }
            }
            Text(stringResource(R.string.obs_value), style = MaterialTheme.typography.titleSmall)
            ChoiceChips(category.options, value, { stableLabel(ObsCategory.optionLabels, it) }) { value = it }
            Field(note, { note = it }, stringResource(R.string.obs_note), minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val v = value ?: return@Button
                        scope.launch { EntryOps.saveObservation(c.store, base, at, category, v, note); onDismiss() }
                    },
                    enabled = value != null && !Backdate.isFuture(at, System.currentTimeMillis()),
                ) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

