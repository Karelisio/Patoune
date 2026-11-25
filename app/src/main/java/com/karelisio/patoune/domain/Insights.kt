package com.karelisio.patoune.domain

import com.karelisio.patoune.data.AirDay
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.Crisis
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Analyse des déclencheurs (pure, testable JVM) : crises par mois, déclencheurs et types les plus fréquents,
 * pollens / AQI les jours de crise, respiration selon les pollens, moment de la journée.
 * Ce sont des corrélations descriptives, pas des causes.
 */
object Insights {
    /** Nombre minimal de jours de chaque côté pour qu'une comparaison ait un sens. */
    const val MIN_DAYS = 3

    data class MonthCount(val month: YearMonth, val count: Int)

    /** [percent] : part des crises où l'élément apparaît (la somme peut dépasser 100 %). */
    data class Share(val label: String, val count: Int, val percent: Int)

    /** Moyenne d'une grandeur sur deux groupes de jours ([with] : jours « exposés »). */
    data class Comparison(val with: Double, val without: Double, val daysWith: Int, val daysWithout: Int)

    enum class DayPart(val label: String, val hours: IntRange) {
        MORNING("le matin", 6..11), AFTERNOON("l'après-midi", 12..17), EVENING("le soir", 18..23), NIGHT("la nuit", 0..5);

        companion object { fun of(hour: Int): DayPart = entries.first { hour in it.hours } }
    }

    data class Summary(
        val total: Int,
        val months: List<MonthCount>,
        val triggers: List<Share>,
        val types: List<Share>,
        /** Pollen max moyen (grains/m³) : jours de crise vs autres jours. */
        val pollen: Comparison?,
        /** AQI européen moyen : jours de crise vs autres jours. */
        val aqi: Comparison?,
        /** Fréquence respiratoire moyenne : jours de pollens forts vs faibles. */
        val breathByPollen: Comparison?,
        val dayParts: Map<DayPart, Int>,
        /** Moment le plus fréquent, null si aucune crise ou égalité en tête. */
        val topDayPart: DayPart?,
    )

    /** Analyse sur [from]..[to] (inclus) ; les mois couverts vont de celui de [from] à celui de [to]. */
    fun summarize(crises: List<Crisis>, breaths: List<BreathCount>, air: List<AirDay>, zone: ZoneId, from: LocalDate, to: LocalDate): Summary {
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val cr = crises.filter { !it.deleted && day(it.startAt) in from..to }
        val br = breaths.filter { !it.deleted && day(it.at) in from..to }
        val days = air.mapNotNull { a -> Dates.parse(a.date)?.takeIf { it in from..to }?.let { it to a } }.toMap()
        val crisisDays = cr.map { day(it.startAt) }.toSet()
        val parts = dayParts(cr, zone)
        return Summary(
            total = cr.size,
            months = perMonth(cr, zone, YearMonth.from(from), YearMonth.from(to)),
            triggers = ranking(cr) { it.triggers },
            types = ranking(cr) { it.types },
            pollen = compare(days.mapValues { it.value.pollenMax }, crisisDays),
            aqi = compare(days.mapValues { it.value.aqi }, crisisDays),
            breathByPollen = breathByPollen(br, days.mapValues { it.value.pollenMax }, zone),
            dayParts = parts,
            topDayPart = top(parts),
        )
    }

    /** Les 12 derniers mois glissants (mois courant inclus) jusqu'à [today]. */
    fun lastYear(crises: List<Crisis>, breaths: List<BreathCount>, air: List<AirDay>, zone: ZoneId, today: LocalDate): Summary =
        summarize(crises, breaths, air, zone, today.minusMonths(11).withDayOfMonth(1), today)

    /** Crises par mois de [first] à [last] inclus ; les mois sans crise valent 0. */
    fun perMonth(crises: List<Crisis>, zone: ZoneId, first: YearMonth, last: YearMonth): List<MonthCount> {
        val counts = crises.groupingBy { YearMonth.from(Instant.ofEpochMilli(it.startAt).atZone(zone)) }.eachCount()
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.map { MonthCount(it, counts[it] ?: 0) }.toList()
    }

    /** Classement des valeurs d'un champ CSV, chaque valeur comptée au plus une fois par crise. */
    fun ranking(crises: List<Crisis>, field: (Crisis) -> String): List<Share> {
        if (crises.isEmpty()) return emptyList()
        val counts = crises.flatMap { c -> field(c).split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct() }.groupingBy { it }.eachCount()
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { Share(it.key, it.value, (it.value * 100.0 / crises.size).roundToInt()) }
    }

    /** Moyenne de [values] les jours de [marked] vs les autres jours disponibles ; null si < [MIN_DAYS] d'un côté. */
    fun compare(values: Map<LocalDate, Double?>, marked: Set<LocalDate>): Comparison? {
        val known = values.mapNotNull { (d, v) -> v?.let { d to it } }
        val (yes, no) = known.partition { it.first in marked }
        if (yes.size < MIN_DAYS || no.size < MIN_DAYS) return null
        return Comparison(yes.map { it.second }.average(), no.map { it.second }.average(), yes.size, no.size)
    }

    /**
     * Fréquence respiratoire moyenne (moyenne des moyennes journalières) les jours où le pollen dépasse
     * la médiane des jours disponibles vs les autres ; null si < [MIN_DAYS] jours mesurés d'un côté.
     */
    fun breathByPollen(breaths: List<BreathCount>, pollen: Map<LocalDate, Double?>, zone: ZoneId): Comparison? {
        val known = pollen.mapNotNull { (d, v) -> v?.let { d to it } }.toMap()
        if (known.isEmpty()) return null
        val med = median(known.values.toList())
        val daily = breaths.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
            .filterKeys { it in known }.mapValues { e -> e.value.map { it.bpm }.average() }
        val (high, low) = daily.entries.partition { known.getValue(it.key) > med }
        if (high.size < MIN_DAYS || low.size < MIN_DAYS) return null
        return Comparison(high.map { it.value }.average(), low.map { it.value }.average(), high.size, low.size)
    }

    fun dayParts(crises: List<Crisis>, zone: ZoneId): Map<DayPart, Int> {
        val counts = crises.groupingBy { DayPart.of(Instant.ofEpochMilli(it.startAt).atZone(zone).hour) }.eachCount()
        return DayPart.entries.associateWith { counts[it] ?: 0 }
    }

    private fun top(parts: Map<DayPart, Int>): DayPart? {
        val max = parts.values.maxOrNull() ?: return null
        if (max == 0) return null
        return parts.filterValues { it == max }.keys.singleOrNull()
    }

    fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    // Phrases partagées par l'écran et le rapport PDF.

    const val NOT_ENOUGH = "Pas encore assez de données."
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.FRENCH)

    fun fmtMonth(m: YearMonth): String = m.format(monthFmt)

    private fun n(v: Double) = v.roundToInt().toString()

    fun pollenSentence(c: Comparison?): String = c?.let {
        "Les jours de crise, le pollen moyen était de ${n(it.with)} grains/m³ contre ${n(it.without)} les autres jours."
    } ?: "Pollens : ${NOT_ENOUGH.lowercase()}"

    fun aqiSentence(c: Comparison?): String = c?.let {
        "Les jours de crise, l'indice de qualité de l'air moyen était de ${n(it.with)} contre ${n(it.without)} les autres jours."
    } ?: "Qualité de l'air : ${NOT_ENOUGH.lowercase()}"

    fun breathSentence(c: Comparison?): String = c?.let {
        "Les jours de pollens forts, la respiration moyenne était de ${n(it.with)} resp/min contre ${n(it.without)} les jours de pollens faibles."
    } ?: "Respiration et pollens : ${NOT_ENOUGH.lowercase()}"

    fun dayPartSentence(s: Summary): String = when {
        s.total == 0 -> "Moment de la journée : aucune crise."
        s.topDayPart == null -> "Pas de moment de la journée dominant."
        else -> "Les crises surviennent le plus souvent ${s.topDayPart.label} (${s.dayParts.getValue(s.topDayPart)} sur ${s.total})."
    }

    fun sharesText(list: List<Share>): String = list.joinToString(", ") { "${it.label} ${it.count} (${it.percent} %)" }
}
