package com.karelisio.patoune.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.karelisio.patoune.R
import com.karelisio.patoune.data.CrisisTypes
import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.ObsCategory
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.domain.Insights
import com.karelisio.patoune.domain.Frequency
import com.karelisio.patoune.domain.MedDiff
import com.karelisio.patoune.domain.ReminderPlanner
import java.time.DayOfWeek
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
                    when {
                        ch.asNeeded -> ctx.getString(R.string.med_change_as_needed)
                        ch.freq != null -> FreqText.withTimes(ctx, ch.freq, ch.times)
                        else -> times(ch.times)
                    },
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
            is MedDiff.Change.Recurrence -> line(R.string.freq_title, FreqText.frequency(ctx, ch.from), FreqText.frequency(ctx, ch.to))
            MedDiff.Change.ToAsNeeded -> ctx.getString(R.string.med_change_to_as_needed)
            is MedDiff.Change.ToFixed -> ctx.getString(R.string.med_change_to_fixed, times(ch.times))
            is MedDiff.Change.Start -> line(R.string.med_change_start, date(ch.from), date(ch.to))
            is MedDiff.Change.End -> line(R.string.med_change_end, date(ch.from), date(ch.to))
            is MedDiff.Change.Alarm -> ctx.getString(if (ch.on) R.string.med_change_alarm_on else R.string.med_change_alarm_off)
        }
    }
}

/**
 * Résumés lisibles de la fréquence d'un traitement : « Tous les jours à 08:00, 20:00 »,
 * « Lun, mer, ven à 08:00 », « Tous les 3 mois, le 12, à 09:00 »… (fiche, urgence, PDF, historique).
 */
object FreqText {
    private val shortRes = mapOf(
        DayOfWeek.MONDAY to R.string.day_short_mon, DayOfWeek.TUESDAY to R.string.day_short_tue,
        DayOfWeek.WEDNESDAY to R.string.day_short_wed, DayOfWeek.THURSDAY to R.string.day_short_thu,
        DayOfWeek.FRIDAY to R.string.day_short_fri, DayOfWeek.SATURDAY to R.string.day_short_sat,
        DayOfWeek.SUNDAY to R.string.day_short_sun,
    )
    private val fullRes = mapOf(
        DayOfWeek.MONDAY to R.string.day_full_mon, DayOfWeek.TUESDAY to R.string.day_full_tue,
        DayOfWeek.WEDNESDAY to R.string.day_full_wed, DayOfWeek.THURSDAY to R.string.day_full_thu,
        DayOfWeek.FRIDAY to R.string.day_full_fri, DayOfWeek.SATURDAY to R.string.day_full_sat,
        DayOfWeek.SUNDAY to R.string.day_full_sun,
    )

    fun dayShort(ctx: Context, d: DayOfWeek): String = ctx.getString(shortRes.getValue(d))
    fun dayFull(ctx: Context, d: DayOfWeek): String = ctx.getString(fullRes.getValue(d))

    /** Libellé des puces de type de fréquence. */
    fun typeLabel(ctx: Context, t: FreqType): String = ctx.getString(
        when (t) {
            FreqType.DAILY -> R.string.freq_type_daily
            FreqType.EVERY_N_DAYS -> R.string.freq_type_every_n_days
            FreqType.WEEKDAYS -> R.string.freq_type_weekdays
            FreqType.EVERY_N_WEEKS -> R.string.freq_type_every_n_weeks
            FreqType.EVERY_N_MONTHS -> R.string.freq_type_every_n_months
        },
    )

    private fun days(ctx: Context, set: Set<DayOfWeek>) = set.sorted().joinToString(", ") { dayShort(ctx, it) }

    /** Fréquence sans horaires ; le booléen indique si un complément (« , le 12 ») a été ajouté. */
    private fun parts(ctx: Context, f: Frequency): Pair<String, Boolean> {
        val res = ctx.resources
        val n = f.interval
        fun detail(base: String, extra: String) = ctx.getString(R.string.freq_with_detail, base, extra) to true
        return when (f.type) {
            FreqType.DAILY -> ctx.getString(R.string.freq_daily) to false
            FreqType.EVERY_N_DAYS ->
                (if (n <= 1) ctx.getString(R.string.freq_daily) else res.getQuantityString(R.plurals.freq_every_n_days, n, n)) to false
            FreqType.WEEKDAYS ->
                if (f.weekdays.isEmpty()) ctx.getString(R.string.freq_daily) to false
                else days(ctx, f.weekdays).replaceFirstChar { it.titlecase(Locale.getDefault()) } to false
            FreqType.EVERY_N_WEEKS -> {
                val base = if (n <= 1) ctx.getString(R.string.freq_weekly) else res.getQuantityString(R.plurals.freq_every_n_weeks, n, n)
                when {
                    f.weekdays.isNotEmpty() -> detail(base, days(ctx, f.weekdays))
                    f.anchor != null -> detail(base, ctx.getString(R.string.freq_on_weekday, dayFull(ctx, f.anchor.dayOfWeek)))
                    else -> base to false
                }
            }
            FreqType.EVERY_N_MONTHS -> {
                val base = if (n <= 1) ctx.getString(R.string.freq_monthly) else res.getQuantityString(R.plurals.freq_every_n_months, n, n)
                f.anchor?.let { detail(base, ctx.getString(R.string.freq_on_month_day, it.dayOfMonth)) } ?: (base to false)
            }
        }
    }

    /** « Tous les 3 mois, le 12 », « Lun, mer, ven »… */
    fun frequency(ctx: Context, f: Frequency): String = parts(ctx, f).first

    /** Fréquence suivie des horaires : « Tous les 3 mois, le 12, à 09:00 ». */
    fun withTimes(ctx: Context, f: Frequency, times: List<java.time.LocalTime>): String {
        val (text, detailed) = parts(ctx, f)
        if (times.isEmpty()) return text
        val t = times.joinToString(", ") { it.format(Dates.time) }
        return ctx.getString(if (detailed) R.string.freq_at_after_detail else R.string.freq_at, text, t)
    }

    /** Résumé du calendrier d'un traitement à horaires fixes. */
    fun summary(ctx: Context, med: Medication): String =
        withTimes(ctx, Frequency.of(med), ReminderPlanner.parseTimes(med.times))
}
