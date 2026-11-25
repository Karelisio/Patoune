package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.ui.fmtQty
import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.data.CareItem
import com.karelisio.patoune.data.CareKind
import com.karelisio.patoune.data.DocKind
import com.karelisio.patoune.data.HealthDocument
import com.karelisio.patoune.data.VetVisit
import com.karelisio.patoune.data.WeightEntry
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.newId
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalTopBar
import com.karelisio.patoune.ui.components.ChartPoint
import com.karelisio.patoune.ui.components.ChoiceChips
import com.karelisio.patoune.ui.components.DateField
import com.karelisio.patoune.ui.components.EmptyState
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.NumberField
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.components.TrendChart
import com.karelisio.patoune.ui.components.cascade
import kotlinx.coroutines.launch

private val tabs = listOf(R.string.health_tab_care, R.string.health_tab_visits, R.string.health_tab_weight, R.string.health_tab_documents)
private val fabs = listOf(R.string.health_fab_care, R.string.health_fab_visit, R.string.health_fab_weight, R.string.health_fab_document)

fun CareKind.icon(): ImageVector = when (this) {
    CareKind.VACCINE -> Icons.Rounded.Vaccines
    CareKind.DEWORM -> Icons.Rounded.BugReport
    CareKind.FLEA -> Icons.Rounded.BugReport
}

@Composable
fun HealthScreen(vm: MainViewModel) {
    val animal by vm.selected.collectAsStateWithLifecycle()
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf<Int?>(null) }
    var editCare by remember { mutableStateOf<CareItem?>(null) }
    var editVisit by remember { mutableStateOf<VetVisit?>(null) }
    val nav = LocalNav.current
    Scaffold(
        topBar = { AnimalTopBar(vm) { IconButton(onClick = { nav.navigate(Routes.Emergency) }) { Icon(Icons.Rounded.LocalHospital, stringResource(R.string.emergency_title)) } } },
        floatingActionButton = {
            if (animal != null) ExtendedFloatingActionButton(onClick = { adding = pager.currentPage }, icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(fabs[pager.currentPage])) })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryScrollableTabRow(pager.currentPage) {
                tabs.forEachIndexed { i, t -> Tab(pager.currentPage == i, { scope.launch { pager.animateScrollToPage(i) } }, text = { Text(stringResource(t)) }) }
            }
            val a = animal ?: return@Column
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> CareList(a.id) { editCare = it }
                    1 -> VisitList(a.id) { editVisit = it }
                    2 -> WeightContent(a.id)
                    else -> DocumentList(a.id)
                }
            }
        }
    }
    val a = animal ?: return
    when (adding) {
        0 -> { editCare = CareItem(animalId = a.id, date = Dates.today().toString()); adding = null }
        1 -> { editVisit = VetVisit(id = newId(), animalId = a.id, date = Dates.today().toString()); adding = null }
        2 -> WeightSheet(a.id) { adding = null }
        3 -> DocumentSheet(a.id) { adding = null }
    }
    editCare?.let { CareSheet(it) { editCare = null } }
    editVisit?.let { VisitSheet(it) { editVisit = null } }
}

@Composable
private fun CareList(animalId: String, onEdit: (CareItem) -> Unit) {
    val c = LocalContext.current.container
    val items by remember(animalId) { c.db.care().observeFor(animalId) }.collectAsState(emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) item { EmptyState(Icons.Rounded.Vaccines, stringResource(R.string.health_care_empty), stringResource(R.string.health_care_empty_body)) }
        items(items, key = { it.id }) { ci ->
            val due = Dates.parse(ci.nextDue)
            val late = due != null && !due.isAfter(Dates.today())
            ListItem(
                modifier = Modifier.cascade(items.indexOf(ci)).clickable { onEdit(ci) },
                leadingContent = { ShapeBadge(ci.kind.icon(), PawShapes.clover, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, 40.dp) },
                headlineContent = { Text(stringResource(R.string.pair_dot, stringResource(ci.kind.labelRes), ci.name)) },
                supportingContent = {
                    Text(if (due != null) stringResource(R.string.health_care_done_due, Dates.fmtDay(ci.date), Dates.fmtDay(ci.nextDue)) else stringResource(R.string.health_care_done, Dates.fmtDay(ci.date)),
                        color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
    }
}

@Composable
private fun CareSheet(initial: CareItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val scope = rememberCoroutineScope()
    var ci by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(CareKind.entries, ci.kind, { stringResource(it.labelRes) }) { ci = ci.copy(kind = it) }
            Field(ci.name, { ci = ci.copy(name = it) }, stringResource(R.string.health_care_product))
            DateField(ci.date, { ci = ci.copy(date = it ?: Dates.today().toString()) }, stringResource(R.string.field_date), clearable = false)
            DateField(ci.nextDue, { ci = ci.copy(nextDue = it) }, stringResource(R.string.health_care_next_due))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1L to R.string.health_plus_1_month, 3L to R.string.health_plus_3_months, 12L to R.string.health_plus_1_year).forEach { (m, l) ->
                    AssistChip({ ci = ci.copy(nextDue = (Dates.parse(ci.date) ?: Dates.today()).plusMonths(m).toString()) }, { Text(stringResource(l)) })
                }
            }
            Field(ci.notes, { ci = ci.copy(notes = it) }, stringResource(R.string.field_notes))
            Row {
                if (initial.id.isNotEmpty()) TextButton(onClick = { scope.launch { c.store.delete(ci); onDismiss() } }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { scope.launch { c.store.save(ci.copy(name = ci.name.ifBlank { res.getString(ci.kind.labelRes) })); onDismiss() } }) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun VisitList(animalId: String, onEdit: (VetVisit) -> Unit) {
    val c = LocalContext.current.container
    val visits by remember(animalId) { c.db.visits().observeFor(animalId) }.collectAsState(emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (visits.isEmpty()) item { EmptyState(Icons.Rounded.LocalHospital, stringResource(R.string.health_visit_empty), stringResource(R.string.health_visit_empty_body)) }
        items(visits, key = { it.id }) { v ->
            val media by remember(v.id) { c.db.media().observeAttached(v.id) }.collectAsState(emptyList())
            SectionCard(Modifier.cascade(visits.indexOf(v)).clickable { onEdit(v) }) {
                Text(stringResource(R.string.pair_dot, Dates.fmtDay(v.date), v.reason.ifBlank { stringResource(R.string.health_fab_visit) }), style = MaterialTheme.typography.titleMedium)
                if (v.report.isNotBlank()) Text(v.report, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                v.nextVisit?.let { Text(stringResource(R.string.health_next_visit_on, Dates.fmtDay(it)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                if (media.isNotEmpty()) MediaStrip(media)
            }
        }
    }
}

@Composable
private fun VisitSheet(initial: VetVisit, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var v by remember { mutableStateOf(initial) }
    val media by remember(v.id) { c.db.media().observeAttached(v.id) }.collectAsState(emptyList())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { c.media.import(uri, v.animalId, AttachType.VISIT, v.id) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(v.date, { v = v.copy(date = it ?: Dates.today().toString()) }, stringResource(R.string.field_date), clearable = false)
            Field(v.reason, { v = v.copy(reason = it) }, stringResource(R.string.health_visit_reason))
            Field(v.report, { v = v.copy(report = it) }, stringResource(R.string.health_visit_report), minLines = 4)
            DateField(v.nextVisit, { v = v.copy(nextVisit = it) }, stringResource(R.string.health_next_visit))
            if (media.isNotEmpty()) MediaStrip(media)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip({ scope.launch { c.store.save(v) }; onDismiss(); nav.navigate(Routes.Camera(false, AttachType.VISIT.name, v.id)) }, { Text(stringResource(R.string.media_photo)) }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) })
                AssistChip({ picker.launch(arrayOf("image/*", "application/pdf", "video/*")) }, { Text(stringResource(R.string.media_attach)) }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) })
            }
            Row {
                if (initial.updatedAt > 0) TextButton(onClick = { scope.launch { c.store.delete(v); onDismiss() } }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { scope.launch { c.store.save(v); onDismiss() } }) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun WeightContent(animalId: String) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val weights by remember(animalId) { c.db.weights().observeFor(animalId) }.collectAsState(emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (weights.isEmpty()) item { EmptyState(Icons.Rounded.MonitorWeight, stringResource(R.string.health_weight_empty), stringResource(R.string.health_weight_empty_body)) }
        else item {
            SectionCard {
                Text(stringResource(R.string.health_weight_current, fmtQty(weights.last().kg)), style = MaterialTheme.typography.titleLarge)
                TrendChart(weights.map { ChartPoint(Dates.startOfDay(java.time.LocalDate.parse(it.date)), it.kg) }, color = MaterialTheme.colorScheme.secondary, unit = "kg")
            }
        }
        items(weights.reversed(), key = { it.id }) { w ->
            ListItem(headlineContent = { Text(stringResource(R.string.weight_kg, fmtQty(w.kg))) }, supportingContent = { Text(Dates.fmtDay(w.date)) },
                trailingContent = { IconButton(onClick = { scope.launch { c.store.delete(w) } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.health_weight_delete, fmtQty(w.kg), Dates.fmtDay(w.date))) } })
        }
    }
}

@Composable
private fun WeightSheet(animalId: String, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var kg by remember { mutableStateOf<Double?>(null) }
    var date by remember { mutableStateOf(Dates.today().toString()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(null, { kg = it }, stringResource(R.string.health_weight_field))
            DateField(date, { date = it ?: Dates.today().toString() }, stringResource(R.string.field_date), clearable = false)
            Button(onClick = { scope.launch { kg?.let { c.store.save(WeightEntry(animalId = animalId, date = date, kg = it)) }; onDismiss() } },
                enabled = kg != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun DocumentList(animalId: String) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val docs by remember(animalId) { c.db.documents().observeFor(animalId) }.collectAsState(emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (docs.isEmpty()) item { EmptyState(Icons.Rounded.Description, stringResource(R.string.health_doc_empty), stringResource(R.string.health_doc_empty_body)) }
        items(docs, key = { it.id }) { d ->
            val attached by remember(d.id) { c.db.media().observeAttached(d.id) }.collectAsState(emptyList())
            val m = attached.firstOrNull { it.id == d.mediaId } ?: attached.firstOrNull()
            ListItem(
                modifier = Modifier.cascade(docs.indexOf(d)).clickable { m?.let { nav.navigate(Routes.MediaViewer(it.id)) } },
                leadingContent = {
                    m?.let { MediaThumb(it, size = 56.dp) } ?: ShapeBadge(Icons.Rounded.Description, PawShapes.gem, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                },
                headlineContent = { Text(d.title.ifBlank { stringResource(d.kind.labelRes) }) },
                supportingContent = { Text(stringResource(R.string.pair_dot, stringResource(d.kind.labelRes), Dates.fmtDay(d.date))) },
                trailingContent = { IconButton(onClick = { scope.launch { c.store.delete(d) } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete_named, d.title.ifBlank { stringResource(d.kind.labelRes) })) } },
            )
        }
    }
}

@Composable
private fun DocumentSheet(animalId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf(HealthDocument(id = newId(), animalId = animalId, date = Dates.today().toString())) }
    var fileName by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            c.media.import(uri, animalId, AttachType.DOCUMENT, d.id)?.let { d = d.copy(mediaId = it.id); fileName = it.mime }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(DocKind.entries, d.kind, { stringResource(it.labelRes) }) { d = d.copy(kind = it) }
            Field(d.title, { d = d.copy(title = it) }, stringResource(R.string.health_doc_title))
            DateField(d.date, { d = d.copy(date = it ?: Dates.today().toString()) }, stringResource(R.string.field_date), clearable = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip({ picker.launch(arrayOf("application/pdf", "image/*")) }, { Text(stringResource(R.string.health_doc_pick)) }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) })
                AssistChip({ scope.launch { c.store.save(d) }; onDismiss(); nav.navigate(Routes.Camera(false, AttachType.DOCUMENT.name, d.id)) }, { Text(stringResource(R.string.health_doc_shoot)) },
                    leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) })
            }
            fileName?.let { Text(stringResource(R.string.health_doc_attached), color = MaterialTheme.colorScheme.primary) }
            Button(onClick = { scope.launch { c.store.save(d.copy(title = d.title.ifBlank { res.getString(d.kind.labelRes) })); onDismiss() } }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }

            Spacer(Modifier.padding(8.dp))
        }
    }
}

