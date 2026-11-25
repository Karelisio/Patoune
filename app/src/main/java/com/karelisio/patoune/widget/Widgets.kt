package com.karelisio.patoune.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.patoune.MainActivity
import com.karelisio.patoune.R
import com.karelisio.patoune.container
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.domain.Breath
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.reminders.DoseResult
import com.karelisio.patoune.reminders.Notif

/** Rafraîchissement des widgets de l'écran d'accueil. */
class Widgets(private val context: Context) {
    suspend fun refresh() { runCatching { PatouneWidget().updateAll(context) } }
}

private data class WidgetState(
    val animal: String?,
    val medId: String?, val medName: String?, val medAt: Long?, val given: Boolean, val givenBy: String?,
    val bpm: Double?, val trend: Breath.Trend, val over: Boolean,
)

/** Widget Glance : prochain médicament (bouton « Donné »), dernière fréquence et tendance, bouton « Compter ». */
class PatouneWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(120.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 200.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { GlanceTheme { Content(state) } }
    }

    private suspend fun load(context: Context): WidgetState {
        val c = context.container
        val s = c.settings.current()
        val animals = c.db.animals().allRows().filter { !it.deleted }
        val a = animals.firstOrNull { it.id == s.selectedAnimalId } ?: animals.firstOrNull()
            ?: return WidgetState(null, null, null, null, false, null, null, Breath.Trend.STABLE, false)
        val now = System.currentTimeMillis()
        val meds = c.db.medications().active().filter { it.animalId == a.id && !it.asNeeded }
        val next = meds.mapNotNull { m ->
            ReminderPlanner.next(ReminderPlanner.parseTimes(m.times), now - 30 * 60_000L, Dates.zone(), Dates.parse(m.startDate), Dates.parse(m.endDate))?.let { m to it }
        }.minByOrNull { it.second }
        val given = next?.let { c.doses.alreadyGiven(it.first, now) }
        val last = c.db.breaths().latest(a.id)
        return WidgetState(
            a.name, next?.first?.id, next?.first?.name, next?.second, given != null, given?.by?.takeIf { s.shared && it.isNotBlank() },
            last.firstOrNull()?.bpm, Breath.trend(last.map { it.bpm }), last.firstOrNull()?.let { Breath.exceeds(it.bpm, a.breathThreshold) } ?: false,
        )
    }

    @Composable
    private fun Content(s: WidgetState) {
        val size = LocalSize.current
        val wide = size.width >= 250.dp
        val tall = size.height >= 200.dp
        Column(
            GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(android.R.dimen.system_app_widget_background_radius).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_paw), null, GlanceModifier.size(18.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(s.animal ?: "Patoune", style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (s.medName != null) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(s.medName, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp), maxLines = 1)
                        Text(if (s.given) "Donné" + (s.givenBy?.let { " par $it" } ?: "") else s.medAt?.let { "À ${Dates.fmtTime(it)}" } ?: "",
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp))
                    }
                    if (!s.given && s.medId != null) Box(
                        GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(20.dp).padding(horizontal = 14.dp, vertical = 8.dp)
                            .clickable(actionRunCallback<GiveAction>(actionParametersOf(GiveAction.MED to s.medId))),
                    ) { Text("Donné", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)) }
                    else if (s.given) Text("✓", style = TextStyle(color = GlanceTheme.colors.tertiary, fontWeight = FontWeight.Bold, fontSize = 20.sp))
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            if (wide || tall || s.medName == null) Row(verticalAlignment = Alignment.CenterVertically) {
                val arrow = when (s.trend) { Breath.Trend.UP -> "↗"; Breath.Trend.DOWN -> "↘"; Breath.Trend.STABLE -> "→" }
                Text(s.bpm?.let { "${Breath.format(it)} resp/min $arrow" } ?: "Pas de mesure",
                    style = TextStyle(color = if (s.over) GlanceTheme.colors.error else GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.defaultWeight())
            Box(
                GlanceModifier.fillMaxWidth().background(GlanceTheme.colors.secondaryContainer).cornerRadius(20.dp).padding(vertical = 8.dp)
                    .clickable(actionStartActivity(Intent().setClassName("com.karelisio.patoune", MainActivity::class.java.name).putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_BREATH)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))),
                contentAlignment = Alignment.Center,
            ) { Text("Compter les respirations", style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
        }
    }
}

/** « Donné » depuis le widget, avec le même garde-fou double dose (notification de confirmation). */
class GiveAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.container
        val med = parameters[MED]?.let { c.db.medications().byId(it) } ?: return
        when (val r = c.doses.give(med, DoseStatus.GIVEN)) {
            is DoseResult.AlreadyGiven -> Notif.simple(context, Notif.CH_REMINDERS, ("dbl" + med.id).hashCode(), "Déjà donné ?",
                "${med.name} a déjà été marqué à ${Dates.fmtTime(r.at)}${if (c.settings.current().shared && r.by.isNotBlank()) " par ${r.by}" else ""}. Ouvrez Patoune pour confirmer une nouvelle prise.")
            is DoseResult.Saved -> Unit
        }
        c.widgets.refresh()
    }

    companion object { val MED = ActionParameters.Key<String>("med") }
}

class PatouneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PatouneWidget()
}

