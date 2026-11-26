package com.karelisio.patoune.widget

import androidx.glance.LocalContext
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.ButtonDefaults
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
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
import com.karelisio.patoune.reminders.scheduleOf
import com.karelisio.patoune.reminders.DoseResult
import com.karelisio.patoune.reminders.Notif

/** Rafraîchissement des widgets de l'écran d'accueil. */
class Widgets(private val context: Context) {
    suspend fun refresh() {
        runCatching { PatouneWidget().updateAll(context) }
        runCatching { CrisisWidgetReceiver.updateAll(context) }
    }
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
        provideContent { PatouneGlanceTheme { Content(state) } }
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
            c.db.scheduleOf(m).next(now - 30 * 60_000L)?.let { m to it }
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
        val ctx = LocalContext.current
        val roomy = size.width >= 250.dp || size.height >= 200.dp
        val count = actionStartActivity(
            Intent().setClassName("com.karelisio.patoune", MainActivity::class.java.name)
                .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_BREATH)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        val colors = GlanceTheme.colors
        Scaffold(
            titleBar = {
                TitleBar(
                    startIcon = ImageProvider(R.drawable.ic_paw),
                    title = s.animal ?: ctx.getString(R.string.app_name),
                    iconColor = colors.primary,
                    textColor = colors.onSurface,
                    actions = {
                        CircleIconButton(ImageProvider(R.drawable.ic_air), ctx.getString(R.string.shortcut_breath), count,
                            backgroundColor = null, contentColor = colors.primary)
                    },
                )
            },
            backgroundColor = colors.widgetBackground,
            horizontalPadding = 12.dp,
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                if (s.medName != null) {
                    // Carte « prochaine prise » sur fond de conteneur, comme dans l'appli.
                    Row(
                        GlanceModifier.fillMaxWidth().background(colors.secondaryContainer).cornerRadius(20.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(s.medName, style = TextStyle(color = colors.onSecondaryContainer, fontWeight = FontWeight.Medium, fontSize = 14.sp), maxLines = 1)
                            Text(
                                when {
                                    s.given -> s.givenBy?.let { ctx.getString(R.string.widget_given_by, it) } ?: ctx.getString(R.string.dose_given)
                                    else -> s.medAt?.let { ctx.getString(R.string.widget_at, Dates.fmtTime(it)) } ?: ""
                                },
                                style = TextStyle(color = colors.onSecondaryContainer, fontSize = 12.sp))
                        }
                        if (!s.given && s.medId != null) {
                            val give = actionRunCallback<GiveAction>(actionParametersOf(GiveAction.MED to s.medId))
                            if (size.width >= 200.dp) FilledButton(ctx.getString(R.string.dose_given), give, icon = ImageProvider(R.drawable.ic_check),
                                colors = ButtonDefaults.buttonColors(colors.primary, colors.onPrimary))
                            else CircleIconButton(ImageProvider(R.drawable.ic_check), ctx.getString(R.string.a11y_mark_given), give, backgroundColor = colors.primary, contentColor = colors.onPrimary)
                        } else if (s.given) {
                            CircleIconButton(ImageProvider(R.drawable.ic_check), ctx.getString(R.string.a11y_already_given), actionRunCallback<RefreshAction>(),
                                backgroundColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer)
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val arrow = when (s.trend) { Breath.Trend.UP -> "↗"; Breath.Trend.DOWN -> "↘"; Breath.Trend.STABLE -> "→" }
                    Text(s.bpm?.let { ctx.getString(R.string.widget_bpm, Breath.format(it), arrow) } ?: ctx.getString(R.string.widget_no_measure),
                        style = TextStyle(color = if (s.over) colors.error else colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.defaultWeight())
                }
                if (roomy || s.medName == null) {
                    Spacer(GlanceModifier.defaultWeight())
                    FilledButton(ctx.getString(R.string.shortcut_breath), count, GlanceModifier.fillMaxWidth(), icon = ImageProvider(R.drawable.ic_air),
                        colors = ButtonDefaults.buttonColors(colors.secondaryContainer, colors.onSecondaryContainer))
                }
                Spacer(GlanceModifier.height(12.dp))
            }
        }
    }
}

/** Rafraîchit simplement le widget (tap sur la coche). */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PatouneWidget().update(context, glanceId)
    }
}

/** « Donné » depuis le widget, avec le même garde-fou double dose (notification de confirmation). */
class GiveAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = context.container
        val med = parameters[MED]?.let { c.db.medications().byId(it) } ?: return
        when (val r = c.doses.give(med, DoseStatus.GIVEN)) {
            is DoseResult.AlreadyGiven -> Notif.simple(context, Notif.CH_REMINDERS, ("dbl" + med.id).hashCode(), context.getString(R.string.med_double_title),
                if (c.settings.current().shared && r.by.isNotBlank()) context.getString(R.string.widget_double_by, med.name, Dates.fmtTime(r.at), r.by)
                else context.getString(R.string.widget_double, med.name, Dates.fmtTime(r.at)))

            is DoseResult.Saved -> Unit
        }
        c.widgets.refresh()
    }

    companion object { val MED = ActionParameters.Key<String>("med") }
}

class PatouneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PatouneWidget()
}

