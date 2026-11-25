package com.karelisio.patoune.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderPlanner {
    fun parseTimes(csv: String): List<LocalTime> = csv.split(",").mapNotNull {
        runCatching { LocalTime.parse(it.trim()) }.getOrNull()
    }.distinct().sorted()

    /**
     * Prochain rappel strictement après [after] dans le fuseau [zone], entre [start] et [end] inclus.
     * L'heure murale est conservée lors des changements d'heure (ZonedDateTime gère le trou et le chevauchement).
     */
    fun next(times: List<LocalTime>, after: Long, zone: ZoneId, start: LocalDate?, end: LocalDate?): Long? {
        if (times.isEmpty()) return null
        val from = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        var day = if (start != null && start.isAfter(from)) start else from
        repeat(400) {
            if (end != null && day.isAfter(end)) return null
            for (t in times.sorted()) {
                val at = ZonedDateTime.of(day, t, zone).toInstant().toEpochMilli()
                if (at > after) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    fun snooze(now: Long, minutes: Int = 15): Long = now + minutes * 60_000L
}
