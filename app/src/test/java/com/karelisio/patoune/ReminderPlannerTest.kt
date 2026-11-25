package com.karelisio.patoune

import com.karelisio.patoune.domain.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val times = ReminderPlanner.parseTimes("20:00, 08:00")

    private fun local(ms: Long, zone: ZoneId = paris) = Instant.ofEpochMilli(ms).atZone(zone)

    @Test fun parsesAndSortsTimes() {
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), times)
    }

    @Test fun nextSameDayThenTomorrow() {
        val now = ZonedDateTime.of(2026, 1, 10, 9, 0, 0, 0, paris).toInstant().toEpochMilli()
        val next = ReminderPlanner.next(times, now, paris, null, null)!!
        assertEquals(LocalTime.of(20, 0), local(next).toLocalTime())
        val after = ReminderPlanner.next(times, next, paris, null, null)!!
        assertEquals(LocalDate.of(2026, 1, 11), local(after).toLocalDate())
        assertEquals(LocalTime.of(8, 0), local(after).toLocalTime())
    }

    @Test fun keepsWallClockAcrossSpringForward() {
        // Passage à l'heure d'été le 29 mars 2026 à 2h.
        val before = ZonedDateTime.of(2026, 3, 28, 21, 0, 0, 0, paris).toInstant().toEpochMilli()
        val next = ReminderPlanner.next(times, before, paris, null, null)!!
        assertEquals(LocalTime.of(8, 0), local(next).toLocalTime())
        assertEquals(LocalDate.of(2026, 3, 29), local(next).toLocalDate())
        // 23 h réelles entre 20h la veille et 8h le jour du changement.
        val prevEvening = ZonedDateTime.of(2026, 3, 28, 20, 0, 0, 0, paris).toInstant().toEpochMilli()
        assertEquals(11 * 3_600_000L, next - prevEvening)
    }

    @Test fun keepsWallClockAcrossFallBack() {
        val before = ZonedDateTime.of(2026, 10, 24, 21, 0, 0, 0, paris).toInstant().toEpochMilli()
        val next = ReminderPlanner.next(times, before, paris, null, null)!!
        assertEquals(LocalTime.of(8, 0), local(next).toLocalTime())
        val prevEvening = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, paris).toInstant().toEpochMilli()
        assertEquals(13 * 3_600_000L, next - prevEvening)
    }

    @Test fun nonExistentTimeDuringGapIsShifted() {
        val t = listOf(LocalTime.of(2, 30))
        val before = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, paris).toInstant().toEpochMilli()
        val next = ReminderPlanner.next(t, before, paris, null, null)!!
        assertEquals(LocalTime.of(3, 30), local(next).toLocalTime())
    }

    @Test fun followsTimezone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = ZonedDateTime.of(2026, 6, 1, 7, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        val next = ReminderPlanner.next(times, now, tokyo, null, null)!!
        assertEquals(LocalTime.of(8, 0), local(next, tokyo).toLocalTime())
    }

    @Test fun respectsStartAndEndDates() {
        val now = ZonedDateTime.of(2026, 6, 1, 7, 0, 0, 0, paris).toInstant().toEpochMilli()
        val next = ReminderPlanner.next(times, now, paris, LocalDate.of(2026, 6, 5), null)!!
        assertEquals(LocalDate.of(2026, 6, 5), local(next).toLocalDate())
        assertNull(ReminderPlanner.next(times, now, paris, null, LocalDate.of(2026, 5, 31)))
    }
}
