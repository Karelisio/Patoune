package com.karelisio.patoune.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Adherence {
    /** Nombre de prises prévues entre [from] et [to] (ms), dans les dates de traitement. */
    fun expected(times: List<LocalTime>, start: LocalDate?, end: LocalDate?, from: Long, to: Long, zone: ZoneId): Int {
        if (times.isEmpty()) return 0
        var day = java.time.Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val last = java.time.Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        var n = 0
        while (!day.isAfter(last)) {
            if ((start == null || !day.isBefore(start)) && (end == null || !day.isAfter(end))) {
                n += times.count { t -> ZonedDateTime.of(day, t, zone).toInstant().toEpochMilli() in from..to }
            }
            day = day.plusDays(1)
        }
        return n
    }

    fun percent(given: Int, expected: Int): Int? = if (expected == 0) null else minOf(100, given * 100 / expected)
}
