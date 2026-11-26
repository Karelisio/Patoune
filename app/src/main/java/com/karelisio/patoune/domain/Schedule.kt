package com.karelisio.patoune.domain

import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.Medication
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Fréquence normalisée d'un traitement (sans les horaires). [anchor] = date de début, qui fixe le jour
 * de la semaine (toutes les N semaines) ou le quantième (tous les N mois).
 */
data class Frequency(
    val type: FreqType = FreqType.DAILY,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val anchor: LocalDate? = null,
) {
    /** Comparaison sans l'ancre (un changement de date de début est un changement à part). */
    val key: Frequency get() = copy(anchor = null)

    companion object {
        const val MAX_INTERVAL = 365

        /**
         * Normalise : intervalle ≥ 1, jours seulement quand ils servent ; « tous les 1 jours » et
         * « certains jours » sans jour coché (ou les 7) reviennent à « tous les jours ».
         */
        fun of(type: FreqType, interval: Int, weekdaysCsv: String, anchor: LocalDate?): Frequency {
            val n = interval.coerceIn(1, MAX_INTERVAL)
            val days = Schedule.parseWeekdays(weekdaysCsv)
            return when (type) {
                FreqType.DAILY -> Frequency(anchor = anchor)
                FreqType.EVERY_N_DAYS -> if (n == 1) Frequency(anchor = anchor) else Frequency(type, n, anchor = anchor)
                FreqType.WEEKDAYS -> if (days.isEmpty() || days.size == 7) Frequency(anchor = anchor) else Frequency(type, 1, days, anchor)
                FreqType.EVERY_N_WEEKS -> Frequency(type, n, days, anchor)
                FreqType.EVERY_N_MONTHS -> Frequency(type, n, anchor = anchor)
            }
        }

        fun of(med: Medication): Frequency = of(med.freqType, med.freqInterval, med.weekdays, Dates.parse(med.startDate))
    }
}

/** Traitement avec sa fréquence normalisée (à appeler avant l'enregistrement). */
fun Medication.normalizedFrequency(): Medication {
    val f = Frequency.of(this)
    return copy(freqType = f.type, freqInterval = f.interval, weekdays = Schedule.weekdaysCsv(f.weekdays))
}

/**
 * Calendrier des prises d'un traitement (logique pure) : horaires muraux [times] chaque jour actif
 * entre [start] et [end] inclus, dans le fuseau [zone]. Les jours actifs dépendent de la fréquence,
 * ancrée sur [anchor] (date de début, ou aujourd'hui si elle est vide) :
 * - EVERY_N_DAYS : anchor + k·N jours ;
 * - WEEKDAYS : jours cochés (aucun = tous) ;
 * - EVERY_N_WEEKS : une semaine sur N (semaines du lundi, comptées depuis celle de l'ancre), le même jour
 *   de semaine que l'ancre ou les jours cochés ;
 * - EVERY_N_MONTHS : même quantième que l'ancre tous les N mois, ramené au dernier jour du mois (31 → 30/28/29).
 * L'heure murale est conservée aux changements d'heure (ZonedDateTime décale l'heure qui tombe dans le trou
 * du printemps et prend la première des deux en automne).
 */
data class Schedule(
    val times: List<LocalTime>,
    val zone: ZoneId,
    val frequency: Frequency = Frequency(),
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    private val sortedTimes = times.distinct().sorted()
    private val anchor: LocalDate = frequency.anchor ?: start ?: LocalDate.now(zone)
    private val n = frequency.interval.coerceIn(1, Frequency.MAX_INTERVAL)

    /** Même calendrier sans bornes de dates (garde-fou double dose, vérification de retard). */
    fun unbounded(): Schedule = copy(frequency = frequency.copy(anchor = anchor), start = null, end = null)

    /** Le jour [day] porte-t-il des prises ? */
    fun isActiveDay(day: LocalDate): Boolean {
        if (start != null && day.isBefore(start)) return false
        if (end != null && day.isAfter(end)) return false
        val f = frequency
        return when (f.type) {
            FreqType.DAILY -> true
            FreqType.EVERY_N_DAYS -> Math.floorMod(ChronoUnit.DAYS.between(anchor, day), n.toLong()) == 0L
            FreqType.WEEKDAYS -> f.weekdays.isEmpty() || day.dayOfWeek in f.weekdays
            FreqType.EVERY_N_WEEKS -> {
                val weeks = ChronoUnit.DAYS.between(monday(anchor), monday(day)) / 7
                val dayOk = if (f.weekdays.isEmpty()) day.dayOfWeek == anchor.dayOfWeek else day.dayOfWeek in f.weekdays
                dayOk && Math.floorMod(weeks, n.toLong()) == 0L
            }
            FreqType.EVERY_N_MONTHS -> {
                val months = (day.year * 12L + day.monthValue) - (anchor.year * 12L + anchor.monthValue)
                Math.floorMod(months, n.toLong()) == 0L && day.dayOfMonth == minOf(anchor.dayOfMonth, day.lengthOfMonth())
            }
        }
    }

    private fun at(day: LocalDate, t: LocalTime): Long = ZonedDateTime.of(day, t, zone).toInstant().toEpochMilli()
    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun dayOccurrences(day: LocalDate): List<Long> = sortedTimes.map { at(day, it) }.distinct()

    /** Nombre de jours à parcourir pour être sûr de croiser un jour actif. */
    private val horizonDays: Long = when (frequency.type) {
        FreqType.DAILY, FreqType.WEEKDAYS -> 7L
        FreqType.EVERY_N_DAYS -> n.toLong()
        FreqType.EVERY_N_WEEKS -> 7L * n
        FreqType.EVERY_N_MONTHS -> 31L * n
    } + 8

    /** Prises prévues entre [from] et [to] inclus (ms), triées. */
    fun occurrencesBetween(from: Long, to: Long): List<Long> {
        if (sortedTimes.isEmpty() || to < from) return emptyList()
        var day = dateOf(from).let { d -> if (start != null && start.isAfter(d)) start else d }
        val last = dateOf(to).let { d -> if (end != null && end.isBefore(d)) end else d }
        val out = ArrayList<Long>()
        while (!day.isAfter(last)) {
            if (isActiveDay(day)) dayOccurrences(day).filterTo(out) { it in from..to }
            day = day.plusDays(1)
        }
        return out.distinct().sorted()
    }

    /** Prochaine prise strictement après [after], ou null (fin de traitement). */
    fun next(after: Long): Long? {
        if (sortedTimes.isEmpty()) return null
        var day = dateOf(after).let { d -> if (start != null && start.isAfter(d)) start else d }
        for (i in 0..horizonDays) {
            if (end != null && day.isAfter(end)) return null
            if (isActiveDay(day)) dayOccurrences(day).firstOrNull { it > after }?.let { return it }
            day = day.plusDays(1)
        }
        return null
    }

    /** Dernière prise strictement avant [before], ou null (avant le début). */
    fun previous(before: Long): Long? {
        if (sortedTimes.isEmpty()) return null
        var day = dateOf(before).let { d -> if (end != null && end.isBefore(d)) end else d }
        for (i in 0..horizonDays) {
            if (start != null && day.isBefore(start)) return null
            if (isActiveDay(day)) dayOccurrences(day).lastOrNull { it < before }?.let { return it }
            day = day.minusDays(1)
        }
        return null
    }

    /** Prises par jour en moyenne sur un cycle (stock restant). */
    fun dosesPerDay(): Double {
        val perDay = sortedTimes.size.toDouble()
        val f = frequency
        return when (f.type) {
            FreqType.DAILY -> perDay
            FreqType.EVERY_N_DAYS -> perDay / n
            FreqType.WEEKDAYS -> perDay * (f.weekdays.size.takeIf { it > 0 } ?: 7) / 7.0
            FreqType.EVERY_N_WEEKS -> perDay * (f.weekdays.size.takeIf { it > 0 } ?: 1) / (7.0 * n)
            FreqType.EVERY_N_MONTHS -> perDay / (DAYS_PER_MONTH * n)
        }
    }

    companion object {
        /** Durée moyenne d'un mois grégorien (365,2425 / 12). */
        const val DAYS_PER_MONTH = 30.436875

        private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Horaires seuls, tous les jours (ancienne API). */
        fun daily(times: List<LocalTime>, zone: ZoneId, start: LocalDate? = null, end: LocalDate? = null) =
            Schedule(times, zone, Frequency(), start, end)

        /** Calendrier d'un traitement ; ancré sur sa date de début, ou sur [today] si elle est vide. */
        fun of(med: Medication, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Schedule {
            val start = Dates.parse(med.startDate)
            val f = Frequency.of(med)
            return Schedule(ReminderPlanner.parseTimes(med.times), zone, f.copy(anchor = start ?: today), start, Dates.parse(med.endDate))
        }

        /** "1,3,5" → lundi, mercredi, vendredi (valeurs invalides ignorées). */
        fun parseWeekdays(csv: String): Set<DayOfWeek> = csv.split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..7 } }
            .map { DayOfWeek.of(it) }.toSortedSet()

        fun weekdaysCsv(days: Set<DayOfWeek>): String = days.map { it.value }.sorted().joinToString(",")
    }
}
