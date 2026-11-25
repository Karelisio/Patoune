package com.karelisio.patoune.ui.screens

import androidx.compose.foundation.clickable
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

enum class Range(val label: String, val days: Long?) { WEEK("7 jours", 7), MONTH("30 jours", 30), ALL("Tout", null) }

fun Breath.Trend.icon(): ImageVector = when (this) {
    Breath.Trend.UP -> Icons.AutoMirrored.Rounded.TrendingUp
    Breath.Trend.DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
    Breath.Trend.STABLE -> Icons.AutoMirrored.Rounded.TrendingFlat
}

fun Breath.Trend.label(): String = when (this) {
    Breath.Trend.UP -> "en hausse"
    Breath.Trend.DOWN -> "en baisse"
    Breath.Trend.STABLE -> "stable"
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
                Text("Fréquence respiratoire", style = MaterialTheme.typography.titleMedium)
                if (last == null) Text("Aucune mesure pour l'instant")
                else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedNumber(Breath.format(last.bpm), MaterialTheme.typography.displayMedium)
                        Spacer(Modifier.width(6.dp))
                        Text("resp/min", Modifier.padding(bottom = 8.dp))
                    }
                    val trend = Breath.trend(counts.takeLast(6).reversed().map { it.bpm })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(trend.icon(), null)
                        Spacer(Modifier.width(4.dp))
                        Text("${trend.label()} · ${Dates.fmtDayTime(last.at)}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            ShapeBadge(Icons.Rounded.Air, PawShapes.soft, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 64.dp)
        }
        Button(onClick = onCount, Modifier.fillMaxWidth()) { Text("Compter les respirations") }
        if (last != null && Breath.exceeds(last.bpm, animal.breathThreshold)) ThresholdBanner(animal, last)
    }
}

@Composable
fun ThresholdBanner(animal: Animal, last: BreathCount) {
    SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text("Dernière mesure ${Breath.format(last.bpm)} resp/min, au-dessus du seuil fixé avec le véto (${animal.breathThreshold}).",
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

    Scaffold(topBar = { AnimalTopBar(vm) }) { pad ->
        val a = animal
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (a == null) { item { EmptyState(Icons.Rounded.Air, "Aucun animal", "Ajoutez un animal pour suivre sa respiration.") }; return@LazyColumn }
            item { BreathSummaryCard(a, counts) { nav.navigate(Routes.BreathCounter()) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { nav.navigate(Routes.BreathCounter(night = true)) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Bedtime, null); Spacer(Modifier.width(6.dp)); Text("Mode nuit")
                    }
                    FilledTonalButton(onClick = { nav.navigate(Routes.BreathAuto) }, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Comptage auto")
                    }
                }
            }
            item {
                SectionCard {
                    Text("Tendance", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Range.entries.forEachIndexed { i, r ->
                            SegmentedButton(r == range, { range = r }, SegmentedButtonDefaults.itemShape(i, Range.entries.size)) { Text(r.label) }
                        }
                    }
                    val from = range.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
                    val shown = counts.filter { it.at >= from }
                    if (shown.isEmpty()) Text("Pas de mesure sur cette période.", style = MaterialTheme.typography.bodyMedium)
                    else {
                        val pts = shown.map { ChartPoint(it.at, it.bpm) }
                        val avg = Breath.movingAverage(shown.map { it.bpm }).mapIndexed { i, v -> ChartPoint(shown[i].at, v) }
                        val airShown = if (showPollen) air.filter { Dates.startOfDay(java.time.LocalDate.parse(it.date)) >= from - 86_400_000L } else emptyList()
                        TrendChart(pts, average = avg, threshold = a.breathThreshold?.toDouble(), markers = crises.map { it.startAt }.filter { it >= from },
                            bands = airBands(airShown), unit = "resp/min")
                        Text("Moyenne ${Breath.format(shown.map { it.bpm }.average())} · max ${Breath.format(shown.maxOf { it.bpm })} · ${shown.size} mesure(s)",
                            style = MaterialTheme.typography.labelLarge)
                        Text("— moyenne mobile · ┄ seuil véto · ● crises" + if (settings?.city != null) " · fond : pollens (${settings?.city})" else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (settings?.city != null) TextButton(onClick = { showPollen = !showPollen }) { Text(if (showPollen) "Masquer les pollens" else "Afficher les pollens") }
                    }
                    VetNote()
                }
            }
            item { Text("Mesures", style = MaterialTheme.typography.titleMedium) }
            items(counts.reversed(), key = { it.id }) { b ->
                ListItem(
                    modifier = Modifier.cascade(0).clickable { },
                    headlineContent = { Text("${Breath.format(b.bpm)} resp/min" + if (b.estimated) " (estimation validée)" else "") },
                    supportingContent = {
                        Text("${Dates.fmtDayTime(b.at)} · ${b.context.label}" + (if (b.note.isNotBlank()) " · ${b.note}" else "") +
                            if (settings?.shared == true && b.createdBy.isNotBlank()) " · ${b.createdBy}" else "")
                    },
                    leadingContent = {
                        val over = Breath.exceeds(b.bpm, a.breathThreshold)
                        ShapeBadge(Icons.Rounded.Air, PawShapes.pad, if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            if (over) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer, 40.dp)
                    },
                    trailingContent = { IconButton(onClick = { scope.launch { c.store.delete(b) } }) { Icon(Icons.Rounded.Delete, "Supprimer") } },
                )
            }
        }
    }
}
