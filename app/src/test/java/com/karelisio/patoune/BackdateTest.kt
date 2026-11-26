package com.karelisio.patoune

import com.karelisio.patoune.domain.Backdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BackdateTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun ms(d: LocalDate, h: Int, m: Int) = ZonedDateTime.of(d, LocalTime.of(h, m), paris).toInstant().toEpochMilli()

    @Test fun recentAndFuture() {
        val now = 10_000_000L
        assertTrue(Backdate.isRecent(now - 59 * 60_000L, now))
        assertFalse(Backdate.isRecent(now - 61 * 60_000L, now))
        assertFalse(Backdate.isFuture(now + 60_000L, now))
        assertTrue(Backdate.isFuture(now + 10 * 60_000L, now))
    }

    @Test fun changeDateKeepsLocalTimeAcrossDst() {
        // 30 mars 2026 8 h 15 (heure d'été) → 28 mars (heure d'hiver) : toujours 8 h 15 locale.
        val v = ms(LocalDate.of(2026, 3, 30), 8, 15)
        assertEquals(ms(LocalDate.of(2026, 3, 28), 8, 15), Backdate.withDate(v, LocalDate.of(2026, 3, 28), paris))
    }

    @Test fun changeTimeKeepsDayAndDropsSeconds() {
        val day = LocalDate.of(2026, 11, 25)
        val v = ms(day, 23, 50) + 42_000
        assertEquals(ms(day, 7, 5), Backdate.withTime(v, LocalTime.of(7, 5, 30), paris))
    }
}
