package com.karelisio.patoune.ui.screens

import com.karelisio.patoune.R
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.text.input.KeyboardType
import com.karelisio.patoune.data.BreathContext
import com.karelisio.patoune.data.BreathInput
import com.karelisio.patoune.data.EntryOps
import com.karelisio.patoune.domain.Backdate
import com.karelisio.patoune.reminders.BreathAlerts
import com.karelisio.patoune.ui.components.ChoiceChips
import com.karelisio.patoune.ui.components.DateTimeField
import com.karelisio.patoune.ui.components.Field
import com.karelisio.patoune.ui.components.NumberField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.patoune.container
import com.karelisio.patoune.data.AirDay
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.domain.Breath
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.ui.LocalNav
import com.karelisio.patoune.ui.MainViewModel
import com.karelisio.patoune.ui.Routes
import com.karelisio.patoune.ui.components.AnimalTopBar
import com.karelisio.patoune.ui.components.AnimatedNumber
import com.karelisio.patoune.ui.components.ChartBand
import com.karelisio.patoune.ui.components.ChartPoint
import com.karelisio.patoune.ui.components.EmptyState
import com.karelisio.patoune.ui.components.PawShapes
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.ShapeBadge
import com.karelisio.patoune.ui.components.TrendChart
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.cascade
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class Range(@StringRes val label: Int, val days: Long?) { WEEK(R.string.range_week, 7), MONTH(R.string.range_month, 30), ALL(R.string.range_all, null) }

fun Breath.Trend.icon(): ImageVector = when (this) {
    Breath.Trend.UP -> Icons.AutoMirrored.Rounded.TrendingUp
    Breath.Trend.DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
    Breath.Trend.STABLE -> Icons.AutoMirrored.Rounded.TrendingFlat
}

@StringRes
fun Breath.Trend.labelRes(): Int = when (this) {
    Breath.Trend.UP -> R.string.trend_up
    Breath.Trend.DOWN -> R.string.trend_down
    Breath.Trend.STABLE -> R.string.trend_stable
}

/** Bandes de pollens (max du jour, normalisé sur 200 grains/m³). */
fun airBands(days: List<AirDay>): List<ChartBand> = days.mapNotNull { d ->
    val p = d.pollenMax ?: return@mapNotNull null
    val start = Dates.startOfDay(java.time.LocalDate.parse(d.date))
    ChartBand(start, 86_400_000L, (p / 200.0).toFloat().coerceIn(0.03f, 1f))
}

@Composable
fun BreathSummaryCard(animal: Animal, counts: List<BreathCount>, onCount: () -> Unit) {
    val last = counts.lastOrNull()
    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.breath_rate), style = MaterialTheme.typography.titleMedium)
                if (last == null) Text(stringResource(R.string.breath_none_yet))
                else {
                    val trend = Breath.trend(counts.takeLast(6).reversed().map { it.bpm })
                    val bpm = Breath.format(last.bpm)
                    val spoken = stringResource(R.string.a11y_breath_rate, bpm, stringResource(trend.labelRes()))
                    // Annonce vivante : TalkBack lit la nouvelle valeur entière quand elle change.
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = spoken
                    }) {
                        AnimatedNumber(bpm, MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.bpm_unit), Modifier.padding(bottom = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(trend.icon(), null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.pair_dot, stringResource(trend.labelRes()), Dates.fmtDayTime(last.at)), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            ShapeBadge(Icons.Rounded.Air, PawShapes.soft, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 64.dp)
        }
        Button(onClick = onCount, Modifier.fillMaxWidth()) { Text(stringResource(R.string.shortcut_breath)) }
        if (last != null && Breath.exceeds(last.bpm, animal.breathThreshold)) ThresholdBanner(animal, last)
    }
}

@Composable
fun ThresholdBanner(animal: Animal, last: BreathCount) {
    SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.breath_over_threshold, Breath.format(last.bpm), animal.breathThreshold ?: 0),
                color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BreathScreen(vm: MainViewModel) {
    val c = LocalContext.current.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val animal by vm.selected.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by remember(animal?.id) { animal?.let { c.db.breaths().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val crises by remember(animal?.id) { animal?.let { c.db.crises().observeFor(it.id) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val air by remember { c.db.air().observeAll() }.collectAsState(emptyList())
    var range by remember { mutableStateOf(Range.WEEK) }
    var showPollen by remember { mutableStateOf(true) }
    /** Mesure à corriger, ou nouvelle mesure (id vide) ajoutée après coup. */
    var editing by remember { mutableStateOf<BreathCount?>(null) }

    Scaffold(topBar = { AnimalTopBar(vm) }) { pad ->
        val a = animal
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (a == null) { item { EmptyState(Icons.Rounded.Air, stringResource(R.string.animals_empty), stringResource(R.string.breath_no_animal_body)) }; return@LazyColumn }
            item { BreathSummaryCard(a, counts) { nav.navigate(Routes.BreathCounter()) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { nav.navigate(Routes.BreathCounter(night = true)) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Bedtime, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.breath_night_mode))
                    }
                    FilledTonalButton(onClick = { nav.navigate(Routes.BreathAuto) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.breath_auto))
                    }
                }
            }
            item {
                SectionCard {
                    Text(stringResource(R.string.breath_trend), style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Range.entries.forEachIndexed { i, r ->
                            SegmentedButton(r == range, { range = r }, SegmentedButtonDefaults.itemShape(i, Range.entries.size)) { Text(stringResource(r.label)) }
                        }
                    }
                    val from = range.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
                    val shown = counts.filter { it.at >= from }
                    if (shown.isEmpty()) Text(stringResource(R.string.breath_none_period), style = MaterialTheme.typography.bodyMedium)
                    else {
                        val pts = shown.map { ChartPoint(it.at, it.bpm) }
                        val avg = Breath.movingAverage(shown.map { it.bpm }).mapIndexed { i, v -> ChartPoint(shown[i].at, v) }
                        val airShown = if (showPollen) air.filter { Dates.startOfDay(java.time.LocalDate.parse(it.date)) >= from - 86_400_000L } else emptyList()
                        TrendChart(pts, average = avg, threshold = a.breathThreshold?.toDouble(), markers = crises.map { it.startAt }.filter { it >= from },
                            bands = airBands(airShown), unit = stringResource(R.string.bpm_unit))
                        Text(pluralStringResource(R.plurals.breath_stats, shown.size, Breath.format(shown.map { it.bpm }.average()), Breath.format(shown.maxOf { it.bpm }), shown.size),
                            style = MaterialTheme.typography.labelLarge)
                        val city = settings?.city
                        Text(if (city != null) stringResource(R.string.breath_legend_pollen, city) else stringResource(R.string.breath_legend),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (city != null) TextButton(onClick = { showPollen = !showPollen }) { Text(stringResource(if (showPollen) R.string.breath_hide_pollen else R.string.breath_show_pollen)) }
                    }
                    VetNote()
                }
            }
            item { Text(stringResource(R.string.breath_measures), style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedButton(onClick = { editing = BreathCount(animalId = a.id, at = System.currentTimeMillis(), context = BreathContext.REST) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.breath_add_past))
                }
            }
            items(counts.reversed(), key = { it.id }) { b ->
                ListItem(
                    modifier = Modifier.cascade(0).clickable(onClickLabel = stringResource(R.string.breath_edit_title)) { editing = b },
                    headlineContent = { Text(stringResource(if (b.estimated) R.string.bpm_value_estimated else R.string.bpm_value, Breath.format(b.bpm))) },
                    supportingContent = {
                        Text(listOfNotNull(Dates.fmtDayTime(b.at), stringResource(b.context.labelRes), b.note.takeIf { it.isNotBlank() },
                            b.createdBy.takeIf { settings?.shared == true && it.isNotBlank() }).joinToString(" · "))
                    },
                    leadingContent = {
                        val over = Breath.exceeds(b.bpm, a.breathThreshold)
                        ShapeBadge(Icons.Rounded.Air, PawShapes.pad, if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer, 40.dp)
                    },
                    trailingContent = { IconButton(onClick = { scope.launch { c.store.delete(b) } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.breath_delete, Breath.format(b.bpm), Dates.fmtDayTime(b.at))) } },

                )
            }
        }
    }
    val a = animal
    editing?.let { b -> if (a != null) BreathSheet(a, b) { editing = null } }
}

private enum class BreathMode { RATE, TAPS }

/**
 * Ajout ([base] sans id) ou correction d'une mesure : fréquence saisie directement, ou respirations comptées sur 15/30/60 s
 * (fréquence recalculée). L'alerte de seuil ne part que pour une mesure de moins d'1 h, nouvelle ou dont la valeur change.
 */
@Composable
private fun BreathSheet(animal: Animal, base: BreathCount, onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val isNew = base.id.isEmpty()
    var at by remember { mutableLongStateOf(base.at) }
    val countedAsTaps = !isNew && base.taps > 0 && base.durationSec in Breath.durations && Breath.perMinute(base.taps, base.durationSec) == base.bpm
    var mode by remember { mutableStateOf(if (countedAsTaps) BreathMode.TAPS else BreathMode.RATE) }
    var rate by remember { mutableStateOf(if (isNew) null else base.bpm) }
    var taps by remember { mutableStateOf(if (countedAsTaps) base.taps.toString() else "") }
    var duration by remember { mutableIntStateOf(if (base.durationSec in Breath.durations) base.durationSec else 30) }
    var context by remember { mutableStateOf(base.context) }
    var note by remember { mutableStateOf(base.note) }
    val input = when (mode) {
        BreathMode.RATE -> rate?.let { BreathInput.Rate(it) }
        BreathMode.TAPS -> taps.toIntOrNull()?.let { BreathInput.Taps(it, duration) }
    }
    val draft = input?.let { EntryOps.breath(base, at, it, context, note) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (isNew) R.string.breath_add_past else R.string.breath_edit_title), style = MaterialTheme.typography.titleLarge)
            DateTimeField(at, { at = it }, stringResource(R.string.field_date))
            val modes = BreathMode.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, m ->
                    SegmentedButton(mode == m, { mode = m }, SegmentedButtonDefaults.itemShape(i, modes.size)) {
                        Text(stringResource(if (m == BreathMode.RATE) R.string.breath_mode_rate else R.string.breath_mode_taps))
                    }
                }
            }
            when (mode) {
                BreathMode.RATE -> NumberField(rate, { rate = it }, stringResource(R.string.breath_rate_field))
                BreathMode.TAPS -> {
                    Field(taps, { t -> taps = t.filter(Char::isDigit) }, stringResource(R.string.breath_taps_field), keyboard = KeyboardType.Number)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Breath.durations.forEachIndexed { i, d ->
                            SegmentedButton(duration == d, { duration = d }, SegmentedButtonDefaults.itemShape(i, Breath.durations.size)) { Text(stringResource(R.string.duration_sec, d)) }
                        }
                    }
                    draft?.let { Text(stringResource(R.string.bpm_value, Breath.format(it.bpm)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
            ChoiceChips(BreathContext.entries, context, { stringResource(it.labelRes) }) { context = it }
            Field(note, { note = it }, stringResource(R.string.obs_note))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val i = input ?: return@Button
                        scope.launch {
                            val saved = EntryOps.saveBreath(c.store, base, at, i, context, note) ?: return@launch
                            if (Backdate.isRecent(saved.at, System.currentTimeMillis()) && (isNew || saved.bpm != base.bpm)) BreathAlerts.check(c, animal, saved)
                            c.hooks.onDataChanged()
                            onDismiss()
                        }
                    },
                    enabled = draft != null && !Backdate.isFuture(at, System.currentTimeMillis()),
                ) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}
