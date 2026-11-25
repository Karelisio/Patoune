package com.karelisio.patoune.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karelisio.patoune.R
import com.karelisio.patoune.data.CrisisTypes
import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.ObsCategory
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.Insights
import com.karelisio.patoune.domain.MedDiff
import java.text.NumberFormat
import java.util.Locale

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Libellés traduits des valeurs stockées en français (clés stables synchronisées entre téléphones)
 * et mises en forme textuelles partagées par l'UI, les notifications et le rapport PDF.
 */
object Labels {
    /** Espèces proposées (valeur stockée → libellé). */
    val species: Map<String, Int> = linkedMapOf(
        "Chat" to R.string.species_cat, "Chien" to R.string.species_dog,
        "Lapin" to R.string.species_rabbit, "Autre" to R.string.species_other,
    )
    val sexes: Map<String, Int> = linkedMapOf("Femelle" to R.string.sex_female, "Mâle" to R.string.sex_male)

    /** Valeur stockée affichable : traduite si connue, sinon telle quelle (ancienne donnée, saisie libre). */
    fun of(ctx: Context, map: Map<String, Int>, key: String): String = map[key]?.let(ctx::getString) ?: key

    /** Liste CSV de clés stockées (types, déclencheurs) → libellés traduits séparés par des virgules. */
    fun csv(ctx: Context, map: Map<String, Int>, csv: String): String =
        csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ") { of(ctx, map, it) }

    fun obs(ctx: Context, value: String): String = of(ctx, ObsCategory.optionLabels, value)
    fun crisis(ctx: Context, value: String): String = of(ctx, CrisisTypes.labels, value)
    fun animalTraits(ctx: Context, vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString(" · ") { of(ctx, species + sexes, it) }
}

@Composable
fun stableLabel(map: Map<String, Int>, key: String): String = map[key]?.let { stringResource(it) } ?: key

/** Quantité lisible selon la locale (1 ; 0,5 / 0.5). */
fun fmtQty(d: Double?): String = d?.let { NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(it) } ?: "—"

/** Nombre à utiliser pour choisir la forme plurielle d'une quantité éventuellement décimale. */
fun pluralCount(d: Double): Int = floor(d).toInt().coerceAtLeast(0)

/** « 3 bouffées », « 1 comprimé »… */
fun unitQty(ctx: Context, type: MedType, qty: Double?): String =
    ctx.resources.getQuantityString(type.unitRes, pluralCount(qty ?: 0.0), fmtQty(qty))

/** Phrases de l'analyse des déclencheurs, partagées par l'écran et le rapport PDF. */
object InsightsText {
    private fun n(v: Double) = v.roundToInt()

    fun dayPart(ctx: Context, p: Insights.DayPart): String = ctx.getString(
        when (p) {
            Insights.DayPart.MORNING -> R.string.daypart_morning
            Insights.DayPart.AFTERNOON -> R.string.daypart_afternoon
            Insights.DayPart.EVENING -> R.string.daypart_evening
            Insights.DayPart.NIGHT -> R.string.daypart_night
        },
    )

    fun pollen(ctx: Context, c: Insights.Comparison?): String =
        c?.let { ctx.getString(R.string.insights_pollen, n(it.with), n(it.without)) } ?: ctx.getString(R.string.insights_pollen_none)

    fun aqi(ctx: Context, c: Insights.Comparison?): String =
        c?.let { ctx.getString(R.string.insights_aqi, n(it.with), n(it.without)) } ?: ctx.getString(R.string.insights_aqi_none)

    fun breath(ctx: Context, c: Insights.Comparison?): String =
        c?.let { ctx.getString(R.string.insights_breath, n(it.with), n(it.without)) } ?: ctx.getString(R.string.insights_breath_none)

    fun dayParts(ctx: Context, s: Insights.Summary): String = when {
        s.total == 0 -> ctx.getString(R.string.insights_daypart_no_crisis)
        s.topDayPart == null -> ctx.getString(R.string.insights_daypart_none)
        else -> ctx.getString(R.string.insights_daypart, dayPart(ctx, s.topDayPart), s.dayParts.getValue(s.topDayPart), s.total)
    }

    fun sentences(ctx: Context, s: Insights.Summary): List<String> =
        listOf(pollen(ctx, s.pollen), aqi(ctx, s.aqi), breath(ctx, s.breathByPollen), dayParts(ctx, s))

    /** « Pollens 3 (75 %), Litière 1 (25 %) » avec les libellés traduits. */
    fun shares(ctx: Context, list: List<Insights.Share>): String =
        list.joinToString(", ") { ctx.getString(R.string.insights_share, Labels.crisis(ctx, it.label), it.count, it.percent) }

    fun months(ctx: Context, months: List<Insights.MonthCount>): String =
        months.joinToString(", ") { ctx.getString(R.string.insights_month_count, Dates.fmtMonth(it.month), it.count) }
}

/** « Bouffées par prise », « Comprimés par prise »… */
fun unitsPerDose(ctx: Context, type: MedType): String =
    ctx.getString(R.string.med_units_per_dose, ctx.getString(type.unitsNameRes).replaceFirstChar { it.titlecase(Locale.getDefault()) })

/** Lignes lisibles de l'historique d'un traitement (enregistrées dans la langue de la personne qui modifie). */
object MedDiffText {
    private fun times(list: List<java.time.LocalTime>) = list.joinToString(", ") { it.format(Dates.time) }.ifEmpty { "—" }
    private fun text(s: String) = s.ifBlank { "—" }
    private fun date(d: java.time.LocalDate?) = d?.format(Dates.day) ?: "—"

    fun lines(ctx: Context, type: MedType, changes: List<MedDiff.Change>): List<String> = changes.map { ch ->
        fun line(label: Int, a: String, b: String) = ctx.getString(R.string.med_change_line, ctx.getString(label), a, b)
        when (ch) {
            is MedDiff.Change.Created -> {
                val parts = listOfNotNull(
                    ch.dose.takeIf { it.isNotEmpty() },
                    if (ch.asNeeded) ctx.getString(R.string.med_change_as_needed) else times(ch.times),
                    ch.start?.let { ctx.getString(R.string.med_change_from, date(it)) },
                    ch.end?.let { ctx.getString(R.string.med_change_until, date(it)) },
                )
                if (parts.isEmpty()) ctx.getString(R.string.med_change_created)
                else ctx.getString(R.string.med_change_created_with, parts.joinToString(", "))
            }
            is MedDiff.Change.Name -> line(R.string.med_change_name, text(ch.from), text(ch.to))
            is MedDiff.Change.Dose -> line(R.string.med_change_dose, text(ch.from), text(ch.to))
            is MedDiff.Change.Units -> ctx.getString(
                R.string.med_change_line, unitsPerDose(ctx, type),
                fmtQty(ch.from), fmtQty(ch.to),
            )
            is MedDiff.Change.Times -> line(R.string.med_change_times, times(ch.from), times(ch.to))
            MedDiff.Change.ToAsNeeded -> ctx.getString(R.string.med_change_to_as_needed)
            is MedDiff.Change.ToFixed -> ctx.getString(R.string.med_change_to_fixed, times(ch.times))
            is MedDiff.Change.Start -> line(R.string.med_change_start, date(ch.from), date(ch.to))
            is MedDiff.Change.End -> line(R.string.med_change_end, date(ch.from), date(ch.to))
            is MedDiff.Change.Alarm -> ctx.getString(if (ch.on) R.string.med_change_alarm_on else R.string.med_change_alarm_off)
        }
    }
}
