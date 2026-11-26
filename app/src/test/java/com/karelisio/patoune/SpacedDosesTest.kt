package com.karelisio.patoune

import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.DoseGuard
import com.karelisio.patoune.domain.Due
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.domain.Schedule
import com.karelisio.patoune.domain.TakenDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fréquences espacées : le cycle repart de la dernière prise donnée ; « à donner » selon l'échéance. */
class SpacedDosesTest {
    private val z = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 26)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()
    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(z).toLocalDateTime().toString()

    private fun med(type: FreqType, interval: Int = 1, times: String = "09:00", start: String = "2026-03-01", weekdays: String = "") =
        Medication(times = times, freqType = type, freqInterval = interval, weekdays = weekdays, startDate = start)

    private val quarterly = med(FreqType.EVERY_N_MONTHS, 3, start = "2026-03-01")

    @Test fun quarterlyGivenLastMondayNextInThreeMonths() {
        // Donné lundi 23 novembre à 18 h 40 : prochaine prise le 23 février, à l'heure du traitement.
        val monday = at(2026, 11, 23, 18, 40)
        val s = Schedule.of(quarterly, z, monday, today)
        assertEquals("2027-02-23T09:00", local(s.next(at(2026, 11, 26, 12))!!))
        assertEquals("2027-02-23T09:00", local(ReminderPlanner.next(quarterly, at(2026, 11, 26, 12), z, monday, today)!!))
        // Sans la prise, l'ancienne ancre (date de début, le 1er) reste en vigueur.
        assertEquals("2026-12-01T09:00", local(Schedule.of(quarterly, z, today).next(at(2026, 11, 26, 12))!!))
    }

    @Test fun everyTenDaysGivenEarlyRestartsFromTheDose() {
        // Début le 1er mars : prise prévue le 26 novembre ; donnée en avance le 23 → la suivante le 3 décembre.
        val m = med(FreqType.EVERY_N_DAYS, 10, times = "08:00")
        assertEquals("2026-11-26T08:00", local(Schedule.of(m, z, today).next(at(2026, 11, 23, 12))!!))
        val given = at(2026, 11, 23, 7, 30)
        val s = Schedule.of(m, z, given, today)
        assertEquals("2026-12-03T08:00", local(s.next(at(2026, 11, 23, 12))!!))
        assertEquals("2026-12-13T08:00", local(s.next(at(2026, 12, 3, 8))!!))
        // Juste après la prise de 7 h 30, l'horaire de 8 h du même jour est couvert par elle : l'échéance affichée est le 3.
        assertEquals("2026-11-23T08:00", local(s.next(given)!!))
        assertEquals("2026-12-03T08:00", local(Due.nextOpen(s, given + 60_000, given)!!))
    }

    @Test fun dailyAndWeekdaysIgnoreTheLastDose() {
        val from = at(2026, 11, 1, 0)
        val to = at(2026, 12, 31, 23)
        for (m in listOf(med(FreqType.DAILY, times = "08:00,20:00"), med(FreqType.WEEKDAYS, weekdays = "1,4"))) {
            val before = Schedule.of(m, z, today).occurrencesBetween(from, to)
            assertEquals(before, Schedule.of(m, z, at(2026, 11, 25, 22), today).occurrencesBetween(from, to))
        }
    }

    @Test fun doseBeforeStartDateStillAnchors() {
        // Cas signalé : traitement créé aujourd'hui (début = aujourd'hui), prise saisie après coup pour lundi dernier.
        val m = med(FreqType.EVERY_N_MONTHS, 3, start = "2026-11-26")
        val monday = at(2026, 11, 23, 9)
        assertEquals("2027-02-23T09:00", local(Schedule.of(m, z, monday, today).next(at(2026, 11, 26, 12))!!))
    }

    @Test fun doseJustGivenIsAlwaysInTheCurrentWindow() {
        // Garde-fou : un second « Donné » juste après demande toujours confirmation, quelle que soit l'heure de la prise.
        for (takenAt in listOf(at(2026, 11, 26, 0, 30), at(2026, 11, 26, 8, 40), at(2026, 11, 26, 9, 0), at(2026, 11, 26, 23, 50))) {
            val s = Schedule.of(quarterly, z, takenAt, today)
            val again = takenAt + 60_000
            assertNotNull(local(takenAt), DoseGuard.alreadyGiven(listOf(TakenDose(takenAt, "")), DoseGuard.currentWindow(s, again)))
        }
    }

    /** Arguments de [Due.isDue] comme les calcule l'écran (prochaine après now − 30 min, dernière avant now). */
    private fun due(m: Medication, given: List<Long>, now: Long): Boolean {
        val s = Schedule.of(m, z, given.maxOrNull(), today)
        val taken = DoseGuard.alreadyGiven(given.map { TakenDose(it, "") }, DoseGuard.currentWindow(s, now))
        return Due.isDue(Due.nextOpen(s, now - 30 * 60_000L, taken?.takenAt), s.previous(now + 1), taken?.takenAt, now)
    }

    @Test fun quarterlyGivenThreeDaysAgoIsNotDue() {
        val now = at(2026, 11, 26, 12)
        assertFalse(due(quarterly, listOf(at(2026, 11, 23, 9, 5)), now))
        // La veille de l'échéance, la prise du trimestre couvre encore la fenêtre en cours ; le jour même, elle est due.
        assertFalse(due(quarterly, listOf(at(2026, 11, 23, 9, 5)), at(2027, 2, 22, 22)))
        assertTrue(due(quarterly, listOf(at(2026, 11, 23, 9, 5)), at(2027, 2, 23, 8, 45)))
        // Prise oubliée : l'échéance du 23 novembre est passée sans prise → due jusqu'à ce qu'elle soit donnée.
        assertTrue(due(quarterly, listOf(at(2026, 8, 23, 9)), now))
    }

    @Test fun dailyInTwoHoursIsDue() {
        val m = med(FreqType.DAILY, times = "14:00")
        assertTrue(due(m, emptyList(), at(2026, 11, 26, 12)))
        // Donnée à 14 h 05 : encore « à donner » 2 min (animation de repli), puis à venir jusqu'au lendemain.
        val given = at(2026, 11, 26, 14, 5)
        assertTrue(due(m, listOf(given), given + 60_000))
        assertFalse(due(m, listOf(given), given + 3 * 60_000))
        assertTrue(due(m, listOf(given), at(2026, 11, 27, 13, 40)))
    }

    @Test fun finishedOrNotStartedIsNotDue() {
        assertFalse(Due.isDue(nextAt = null, currentAt = at(2026, 11, 1, 9), givenAt = null, now = at(2026, 11, 26, 12)))
        assertFalse(Due.isDue(nextAt = at(2026, 12, 10, 9), currentAt = null, givenAt = null, now = at(2026, 11, 26, 12)))
    }
}
