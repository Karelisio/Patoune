package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.ui.unitsPerDose
import com.karelisio.patoune.ui.unitQty
import com.karelisio.patoune.ui.fmtQty
import com.karelisio.patoune.ui.MedDiffText
import com.karelisio.patoune.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import com.karelisio.patoune.AppContainer
import com.karelisio.patoune.container
import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.MedicationChange
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.MedDiff
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.domain.Stock
import com.karelisio.patoune.domain.TakenDose
import com.karelisio.patoune.reminders.DoseResult
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalTopBar
import com.karelisio.patoune.ui.components.AppPermission
import com.karelisio.patoune.ui.components.Perms
import com.karelisio.patoune.ui.components.rememberPermissionRequester
import com.karelisio.patoune.ui.components.rememberResumeTick
import com.karelisio.patoune.ui.components.ChoiceChips
import com.karelisio.patoune.ui.components.DateField
import com.karelisio.patoune.ui.components.DoneButton
import com.karelisio.patoune.ui.components.EmptyState
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.NumberField
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.components.TimesEditor
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.cascade
import com.karelisio.patoune.ui.sharedKey
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

fun MedType.icon(): ImageVector = when (this) {
    MedType.INHALER -> Icons.Rounded.Air
    MedType.TABLET -> Icons.Rounded.Medication
    MedType.LIQUID -> Icons.Rounded.WaterDrop
    MedType.OTHER -> Icons.Rounded.LocalDrink
}


/** État d'un traitement pour l'affichage : prochaine prise et prise déjà faite dans la fenêtre. */
data class MedStatus(val med: Medication, val nextAt: Long?, val given: TakenDose?)

suspend fun medStatus(c: AppContainer, med: Medication): MedStatus {
    val now = System.currentTimeMillis()
    val next = ReminderPlanner.next(ReminderPlanner.parseTimes(med.times), now - 30 * 60_000L, Dates.zone(), Dates.parse(med.startDate), Dates.parse(med.endDate))
    return MedStatus(med, next, c.doses.alreadyGiven(med, now))
}

/** Gère le clic « Donné » avec le garde-fou double dose (dialogue de confirmation). */
class GiveController(private val c: AppContainer, private val scope: kotlinx.coroutines.CoroutineScope) {
    var conflict by mutableStateOf<Pair<Medication, DoseResult.AlreadyGiven>?>(null)
    fun give(med: Medication, force: Boolean = false, onSaved: () -> Unit = {}) = scope.launch {
        when (val r = c.doses.give(med, DoseStatus.GIVEN, force = force)) {
            is DoseResult.AlreadyGiven -> conflict = med to r
            is DoseResult.Saved -> onSaved()
        }
    }
}

@Composable
fun DoubleDoseDialog(ctrl: GiveController, shared: Boolean, onSaved: () -> Unit = {}) {
    val (med, r) = ctrl.conflict ?: return
    AlertDialog(
        onDismissRequest = { ctrl.conflict = null },
        title = { Text(stringResource(R.string.med_double_title)) },
        text = {
            Text(
                if (shared && r.by.isNotBlank()) stringResource(R.string.med_double_body_by, med.name, Dates.fmtTime(r.at), r.by, Dates.fmtDayTime(r.at))
                else stringResource(R.string.med_double_body, med.name, Dates.fmtTime(r.at), Dates.fmtDayTime(r.at)),
            )
        },
        confirmButton = { TextButton(onClick = { ctrl.conflict = null; ctrl.give(med, force = true, onSaved) }) { Text(stringResource(R.string.med_double_confirm)) } },
        dismissButton = { TextButton(onClick = { ctrl.conflict = null }) { Text(stringResource(R.string.action_no)) } },
    )
}

@Composable
fun MedCard(status: MedStatus, shared: Boolean, onGive: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val m = status.med
    SectionCard(modifier.sharedKey("med-${m.id}").clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(m.type.icon(), PawShapes.pad, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                Text(m.dose.ifBlank { stringResource(m.type.labelRes) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val line = status.given?.let { g ->
                    if (shared && g.by.isNotBlank()) stringResource(R.string.med_given_at_by, Dates.fmtTime(g.takenAt), g.by)
                    else stringResource(R.string.med_given_at, Dates.fmtTime(g.takenAt))
                } ?: if (m.asNeeded) stringResource(R.string.med_as_needed)
                else status.nextAt?.let { stringResource(R.string.med_next_at, Dates.fmtDayTime(it)) } ?: stringResource(R.string.med_finished)
                Text(line, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            DoneButton(done = status.given != null, onClick = onGive)
        }
        if (m.stockRemaining != null) {
            val low = Stock.isLow(m.stockRemaining, m.stockAlertAt)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Inventory2, null, Modifier.padding(end = 6.dp), tint = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                val ctx = LocalContext.current
                Text(stringResource(if (low) R.string.med_stock_low else R.string.med_stock, unitQty(ctx, m.type, m.stockRemaining)),
                    style = MaterialTheme.typography.labelMedium, color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TreatmentsScreen(vm: MainViewModel) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val animal by vm.selected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val meds by remember(animal?.id) { animal?.let { c.db.medications().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val doses by remember(animal?.id) { animal?.let { c.db.doses().observeSince(it.id, System.currentTimeMillis() - 2 * 86_400_000L) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    var statuses by remember { mutableStateOf(emptyList<MedStatus>()) }
    LaunchedEffect(meds, doses) { statuses = meds.map { medStatus(c, it) } }
    val scope = rememberCoroutineScope()
    val ctrl = remember { GiveController(c, scope) }

    Scaffold(
        topBar = { AnimalTopBar(vm) },
        floatingActionButton = {
            if (animal != null) ExtendedFloatingActionButton(onClick = { nav.navigate(Routes.MedicationEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.med_fab)) })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (statuses.isEmpty()) item { EmptyState(Icons.Rounded.Medication, stringResource(R.string.med_empty), stringResource(R.string.med_empty_body)) }
            items(statuses, key = { it.med.id }) { s ->
                MedCard(s, settings?.shared == true, onGive = { ctrl.give(s.med) }, onOpen = { nav.navigate(Routes.MedicationDetail(s.med.id)) }, modifier = Modifier.cascade(statuses.indexOf(s)).animateItem())
            }
        }
    }
    DoubleDoseDialog(ctrl, settings?.shared == true)
}

@Composable
fun MedicationEditScreen(vm: MainViewModel, id: String?) {
    val ctx = LocalContext.current
    val res = LocalResources.current
    val c = ctx.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    var m by remember { mutableStateOf(Medication(startDate = Dates.today().toString())) }
    var trackStock by remember { mutableStateOf(false) }
    /** Version enregistrée avant modification, pour l'historique du traitement. */
    var original by remember { mutableStateOf<Medication?>(null) }
    LaunchedEffect(id) { if (id != null) c.db.medications().byId(id)?.let { m = it; original = it; trackStock = it.stockRemaining != null } }

    Scaffold(topBar = { BackTopBar(stringResource(if (id == null) R.string.med_new else R.string.action_edit)) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(m.name, { m = m.copy(name = it) }, stringResource(R.string.med_name))
            ChoiceChips(MedType.entries, m.type, { stringResource(it.labelRes) }) { m = m.copy(type = it) }

            Field(m.dose, { m = m.copy(dose = it) }, stringResource(R.string.med_dose_hint))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.med_as_needed_only), Modifier.weight(1f))
                Switch(m.asNeeded, { m = m.copy(asNeeded = it) })
            }
            if (!m.asNeeded) {
                Text(stringResource(R.string.med_change_times), style = MaterialTheme.typography.titleMedium)
                TimesEditor(m.times) { m = m.copy(times = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.med_alarm_style), Modifier.weight(1f))
                    Switch(m.alarmStyle, { m = m.copy(alarmStyle = it) })
                }
                if (m.alarmStyle) FullScreenPermissionHint()
                // Clé : le champ se réinitialise une fois le traitement existant chargé.
                key(original?.id) {
                    NumberField(m.lateMinutes.toDouble(), { m = m.copy(lateMinutes = it?.toInt()?.coerceIn(0, 24 * 60) ?: 0) }, stringResource(R.string.med_late_minutes))
                }
                VetNote(stringResource(R.string.med_late_note))
            }
            DateField(m.startDate, { m = m.copy(startDate = it ?: Dates.today().toString()) }, stringResource(R.string.med_change_start), clearable = false)
            DateField(m.endDate, { m = m.copy(endDate = it) }, stringResource(R.string.med_end_optional))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.med_track_stock), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(trackStock, { trackStock = it })
            }
            AnimatedVisibility(trackStock, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(m.unitsPerDose, { m = m.copy(unitsPerDose = it ?: 1.0) }, unitsPerDose(ctx, m.type))
                    NumberField(m.stockRemaining, { m = m.copy(stockRemaining = it) }, stringResource(R.string.med_stock_remaining, stringResource(m.type.unitsNameRes)))
                    NumberField(m.stockAlertAt, { m = m.copy(stockAlertAt = it) }, stringResource(R.string.med_stock_alert))
                    DateField(m.renewalDate, { m = m.copy(renewalDate = it) }, stringResource(R.string.med_renewal))
                }
            }
            Field(m.notes, { m = m.copy(notes = it) }, stringResource(R.string.field_notes), minLines = 3)
            Button(
                onClick = {
                    scope.launch {
                        val aid = m.animalId.ifEmpty { animal?.id ?: return@launch }
                        val saved = c.store.save(m.copy(animalId = aid, name = m.name.ifBlank { res.getString(m.type.labelRes) },
                            stockRemaining = if (trackStock) m.stockRemaining ?: 0.0 else null, stockAlertAt = if (trackStock) m.stockAlertAt else null))
                        // Résumé enregistré (et synchronisé) dans la langue de la personne qui modifie.
                        val changes = MedDiffText.lines(ctx, saved.type, MedDiff.diff(original, saved))
                        if (changes.isNotEmpty()) {
                            c.store.save(MedicationChange(medicationId = saved.id, animalId = saved.animalId, at = System.currentTimeMillis(), summary = changes.joinToString("\n")))
                        }
                        c.reminders.schedule(saved)
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
fun MedicationDetailScreen(vm: MainViewModel, id: String) {
    val ctx = LocalContext.current
    val c = ctx.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val med by remember { c.db.medications().observe(id) }.collectAsState(null)
    val logs by remember { c.db.doses().observeFor(id) }.collectAsState(emptyList())
    val changes by remember { c.db.medicationChanges().observeFor(id) }.collectAsState(emptyList())
    var status by remember { mutableStateOf<MedStatus?>(null) }
    var refill by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctrl = remember { GiveController(c, scope) }
    LaunchedEffect(med, logs) { med?.let { status = medStatus(c, it) } }
    val m = med ?: return
    val shared = settings?.shared == true

    Scaffold(topBar = {
        BackTopBar(m.name) {
            IconButton(onClick = { nav.navigate(Routes.MedicationEdit(id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { status?.let { MedCard(it, shared, onGive = { ctrl.give(m) }, onOpen = {}) } }
            if (m.stockRemaining != null) item {
                SectionCard {
                    Text(stringResource(R.string.med_stock_title), style = MaterialTheme.typography.titleMedium)
                    val days = Stock.daysLeft(m.stockRemaining, m.unitsPerDose, ReminderPlanner.parseTimes(m.times).size)
                    val left = unitQty(ctx, m.type, m.stockRemaining)
                    Text(days?.let { stringResource(R.string.med_stock_left_days, left, pluralStringResource(R.plurals.days_about, it, it)) }
                        ?: stringResource(R.string.med_stock_left, left))
                    m.stockAlertAt?.let { alert ->
                        LinearProgressIndicator(progress = { ((m.stockRemaining - alert) / (alert * 4)).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    m.renewalDate?.let { Text(stringResource(R.string.med_renewal_on, Dates.fmtDay(it)), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = { refill = true }) { Text(stringResource(R.string.med_refill_button)) }
                }
            }
            if (m.notes.isNotBlank()) item { SectionCard { Text(m.notes) } }
            if (changes.isNotEmpty()) {
                item { Text(stringResource(R.string.med_history_changes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(changes, key = { it.id }) { ch -> ChangeRow(ch, shared) }
            }
            item { Text(stringResource(R.string.med_history_doses), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(logs, key = { it.id }) { log -> DoseRow(log, shared) { scope.launch { c.doses.undo(log) } } }
        }
    }
    DoubleDoseDialog(ctrl, shared)
    if (refill) {
        var amount by remember { mutableStateOf<Double?>(null) }
        AlertDialog(
            onDismissRequest = { refill = false },
            title = { Text(stringResource(R.string.med_refill_title)) },
            text = { NumberField(null, { amount = it }, stringResource(R.string.med_refill_field, stringResource(m.type.unitsNameRes))) },
            confirmButton = { TextButton(onClick = { scope.launch { amount?.let { c.store.save(m.copy(stockRemaining = it)) }; refill = false } }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { refill = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_named_q, m.name)) },
        confirmButton = { TextButton(onClick = { scope.launch { c.reminders.cancel(m.id); c.store.delete(m); nav.popBackStack() } }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Rappel discret si l'autorisation « Rappels plein écran » manque (Android 14+). */
@Composable
private fun FullScreenPermissionHint() {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var refresh by remember { mutableIntStateOf(0) }
    val granted = remember(tick, refresh) { Perms.granted(ctx, AppPermission.FULL_SCREEN) }
    val request = rememberPermissionRequester { refresh++ }
    if (!granted) TextButton(onClick = { request(AppPermission.FULL_SCREEN) }) { Text(stringResource(R.string.med_allow_full_screen)) }
}

@Composable
private fun ChangeRow(change: MedicationChange, shared: Boolean) {
    ListItem(
        headlineContent = { Text(change.summary) },
        supportingContent = {
            Text(if (shared && change.createdBy.isNotBlank()) stringResource(R.string.pair_dot, Dates.fmtDayTime(change.at), change.createdBy) else Dates.fmtDayTime(change.at))
        },
    )
}

@Composable
private fun DoseRow(log: DoseLog, shared: Boolean, onUndo: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(if (log.status == DoseStatus.GIVEN) R.string.dose_given else R.string.dose_skipped)) },
        supportingContent = {
            Text(if (shared && log.createdBy.isNotBlank()) stringResource(R.string.pair_dot, Dates.fmtDayTime(log.takenAt), log.createdBy) else Dates.fmtDayTime(log.takenAt))
        },
        trailingContent = { IconButton(onClick = onUndo) { Icon(Icons.Rounded.Undo, stringResource(R.string.dose_undo)) } },

    )
}
