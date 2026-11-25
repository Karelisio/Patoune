package com.karelisio.patoune.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.patoune.data.AirDay
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.Insights
import com.karelisio.patoune.ui.components.SectionCard
import com.karelisio.patoune.ui.components.VetNote
import com.karelisio.patoune.ui.components.rememberReduceMotion
import java.util.Locale

/** Carte repliable « Analyse » en tête de la liste des crises (12 derniers mois). */
@Composable
fun InsightsCard(crises: List<Crisis>, breaths: List<BreathCount>, air: List<AirDay>, modifier: Modifier = Modifier) {
    val s = remember(crises, breaths, air) { Insights.lastYear(crises, breaths, air, Dates.zone(), Dates.today()) }
    val reduce = rememberReduceMotion()
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, if (reduce) tween(0) else tween(250), label = "chevron")
    SectionCard(modifier) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Insights, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Analyse", style = MaterialTheme.typography.titleMedium)
                Text("${s.total} crise(s) sur 12 mois", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ExpandMore, if (open) "Replier l'analyse" else "Déplier l'analyse", Modifier.rotate(turn))
        }
        AnimatedVisibility(
            open,
            enter = if (reduce) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reduce) ExitTransition.None else shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Crises par mois", style = MaterialTheme.typography.labelLarge)
                MonthBars(s.months, reduce)
                if (s.triggers.isNotEmpty()) {
                    Text("Déclencheurs notés", style = MaterialTheme.typography.labelLarge)
                    CountChips(s.triggers)
                }
                if (s.types.isNotEmpty()) {
                    Text("Types de crise", style = MaterialTheme.typography.labelLarge)
                    CountChips(s.types)
                }
                Text("Comparaisons", style = MaterialTheme.typography.labelLarge)
                listOf(Insights.pollenSentence(s.pollen), Insights.aqiSentence(s.aqi), Insights.breathSentence(s.breathByPollen), Insights.dayPartSentence(s))
                    .forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                VetNote("Corrélation n'est pas causalité : à discuter avec votre véto.")
            }
        }
    }
}

@Composable
private fun CountChips(list: List<Insights.Share>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { sh ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Text("${sh.label} · ${sh.count}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Mini-graphique en barres : une barre par mois, initiale du mois dessous, nombre au-dessus. */
@Composable
private fun MonthBars(months: List<Insights.MonthCount>, reduce: Boolean) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val grow = remember(months) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(grow) { grow.animateTo(1f, tween(700)) }
    val max = (months.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val desc = months.joinToString(", ") { "${Insights.fmtMonth(it.month)} : ${it.count}" }
    Canvas(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = "Crises par mois. $desc" }) {
        if (months.isEmpty()) return@Canvas
        val textH = 14.sp.toPx()
        val top = textH; val bottom = size.height - textH
        val slot = size.width / months.size
        val barW = slot * 0.6f
        months.forEachIndexed { i, m ->
            val x = i * slot + (slot - barW) / 2
            val h = (bottom - top) * m.count / max * grow.value
            if (m.count > 0) {
                drawRoundRect(colors.primary, Offset(x, bottom - h), Size(barW, h), CornerRadius(4.dp.toPx()))
                val t = measurer.measure(m.count.toString(), label)
                drawText(t, topLeft = Offset(x + (barW - t.size.width) / 2, bottom - h - t.size.height))
            } else {
                drawLine(colors.outlineVariant, Offset(x, bottom - 1f), Offset(x + barW, bottom - 1f), 2.dp.toPx())
            }
            val initial = m.month.month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.FRENCH).uppercase(Locale.FRENCH)
            val l = measurer.measure(initial, label)
            drawText(l, topLeft = Offset(i * slot + (slot - l.size.width) / 2, bottom + 2f))
        }
    }
}
