package com.karelisio.patoune.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object Adherence {
    /** Nombre de prises quotidiennes prévues entre [from] et [to] (ms), dans les dates de traitement. */
    fun expected(times: List<LocalTime>, start: LocalDate?, end: LocalDate?, from: Long, to: Long, zone: ZoneId): Int =
        expected(Schedule.daily(times, zone, start, end), from, to)

    /** Nombre d'occurrences réelles du calendrier (fréquence comprise) entre [from] et [to] inclus. */
    fun expected(schedule: Schedule, from: Long, to: Long): Int = schedule.occurrencesBetween(from, to).size

    fun percent(given: Int, expected: Int): Int? = if (expected == 0) null else minOf(100, given * 100 / expected)
}
