package com.karelisio.patoune

import com.karelisio.patoune.domain.DoseGuard
import com.karelisio.patoune.domain.TakenDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DoseGuardTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
    private fun at(h: Int, m: Int = 0, day: Int = 10) = ZonedDateTime.of(2026, 3, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun detectsDoseAlreadyGivenInCurrentWindow() {
        val window = DoseGuard.currentWindow(times, at(8, 20), zone)
        val given = DoseGuard.alreadyGiven(listOf(TakenDose(at(8, 2), "Camille")), window)
        assertNotNull(given)
        assertEquals("Camille", given!!.by)
    }

    @Test fun earlyDoseCountsForUpcomingSlot() {
        // Prise donnée à 7h50, on retente à 8h05 : même fenêtre.
        val window = DoseGuard.currentWindow(times, at(8, 5), zone)
        assertNotNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(7, 50), "")), window))
    }

    @Test fun previousSlotDoesNotBlock() {
        val window = DoseGuard.currentWindow(times, at(20, 10), zone)
        assertNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(8, 0), "")), window))
    }

    @Test fun previousDayEveningDoesNotBlockMorning() {
        val window = DoseGuard.currentWindow(times, at(8, 0, day = 11), zone)
        assertNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(20, 0, day = 10), "")), window))
    }

    @Test fun asNeededUsesFourHourWindow() {
        val now = at(12)
        val window = DoseGuard.currentWindow(emptyList(), now, zone, asNeeded = true)
        assertNotNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(9), "")), window))
        assertNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(7), "")), window))
    }
}
