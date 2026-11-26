package com.karelisio.patoune

import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.Adherence
import com.karelisio.patoune.domain.DoseGuard
import com.karelisio.patoune.domain.HandledDose
import com.karelisio.patoune.domain.LateCheck
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.domain.Schedule
import com.karelisio.patoune.domain.Stock
import com.karelisio.patoune.domain.TakenDose
import com.karelisio.patoune.domain.normalizedFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Calendrier des prises au-delà du quotidien (tous les N jours, certains jours, N semaines, N mois). */
class ScheduleTest {
    private val z = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 26)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()
    private fun local(ms: Long) = Instant.ofEpochMilli(ms).atZone(z).toLocalDateTime().toString()

    private fun med(type: FreqType, interval: Int = 1, weekdays: String = "", times: String = "08:00", start: String = "2026-03-01", end: String? = null) =
        Medication(times = times, freqType = type, freqInterval = interval, weekdays = weekdays, startDate = start, endDate = end)

    private fun sched(m: Medication) = Schedule.of(m, z, today)

    @Test fun everyTwoDays() {
        val s = sched(med(FreqType.EVERY_N_DAYS, 2))
        assertEquals(listOf(1, 3, 5, 7, 9), s.occurrencesBetween(at(2026, 3, 1, 0), at(2026, 3, 10, 23)).map { Instant.ofEpochMilli(it).atZone(z).dayOfMonth })
        assertEquals(at(2026, 3, 3, 8), s.next(at(2026, 3, 1, 8)))
        assertEquals(at(2026, 3, 3, 8), s.previous(at(2026, 3, 4, 12)))
        // Avant le début : rien avant, première prise le jour du début.
        assertNull(s.previous(at(2026, 3, 1, 7)))
        assertEquals(at(2026, 3, 1, 8), s.next(at(2026, 2, 20, 12)))
        // La surcharge de ReminderPlanner passe par le même calendrier.
        assertEquals(at(2026, 3, 3, 8), ReminderPlanner.next(med(FreqType.EVERY_N_DAYS, 2), at(2026, 3, 1, 8), z, today))
    }

    @Test fun mondayAndThursday() {
        val s = sched(med(FreqType.WEEKDAYS, weekdays = "1,4"))
        val first = s.next(at(2026, 3, 1, 0))!!
        assertEquals("2026-03-02T08:00", local(first))
        assertEquals("2026-03-05T08:00", local(s.next(first)!!))
        assertEquals(4, s.occurrencesBetween(at(2026, 3, 1, 0), at(2026, 3, 14, 23)).size)
        assertEquals("2026-03-12T08:00", local(s.previous(at(2026, 3, 15, 12))!!))
    }

    @Test fun everyTwoWeeks() {
        // Début un mardi, aucun jour coché : le mardi, une semaine sur deux.
        val s = sched(med(FreqType.EVERY_N_WEEKS, 2, times = "09:00", start = "2026-03-03"))
        assertEquals(listOf("2026-03-03T09:00", "2026-03-17T09:00", "2026-03-31T09:00"),
            s.occurrencesBetween(at(2026, 3, 1, 0), at(2026, 4, 1, 0)).map(::local))
        // Jours cochés (lun, ven) dans les semaines actives ; le lundi précédant le début ne compte pas.
        val days = sched(med(FreqType.EVERY_N_WEEKS, 2, weekdays = "1,5", times = "09:00", start = "2026-03-03"))
        assertEquals(listOf("2026-03-06", "2026-03-16", "2026-03-20", "2026-03-30", "2026-04-03"),
            days.occurrencesBetween(at(2026, 3, 1, 0), at(2026, 4, 3, 23)).map { local(it).substring(0, 10) })
    }

    @Test fun monthlyAnchoredOn31() {
        val s = sched(med(FreqType.EVERY_N_MONTHS, 1, times = "09:00", start = "2026-01-31"))
        var t = at(2026, 1, 31, 9)
        val seen = (1..4).map { t = s.next(t)!!; local(t) }
        assertEquals(listOf("2026-02-28T09:00", "2026-03-31T09:00", "2026-04-30T09:00", "2026-05-31T09:00"), seen)
        // Année bissextile : 29 février.
        val leap = sched(med(FreqType.EVERY_N_MONTHS, 1, times = "09:00", start = "2028-01-31"))
        assertEquals("2028-02-29T09:00", local(leap.next(at(2028, 1, 31, 9))!!))
    }

    @Test fun quarterly() {
        val s = sched(med(FreqType.EVERY_N_MONTHS, 3, times = "09:00", start = "2026-01-12"))
        assertEquals(listOf("2026-01-12", "2026-04-12", "2026-07-12", "2026-10-12"),
            s.occurrencesBetween(at(2026, 1, 1, 0), at(2026, 12, 31, 23)).map { local(it).substring(0, 10) })
        assertEquals(at(2026, 4, 12, 9), s.previous(at(2026, 6, 1, 0)))
        assertEquals(at(2026, 7, 12, 9), s.next(at(2026, 4, 12, 9)))
    }

    @Test fun wallClockKeptAcrossDstChanges() {
        // Printemps (29 mars 2026) : tous les 2 jours à 08:00, toujours 08:00 heure locale (47 h d'écart).
        val spring = sched(med(FreqType.EVERY_N_DAYS, 2, start = "2026-03-27"))
        val next = spring.next(at(2026, 3, 27, 8))!!
        assertEquals("2026-03-29T08:00", local(next))
        assertEquals(47 * 3_600_000L, next - at(2026, 3, 27, 8))
        // Heure dans le trou du printemps : décalée après le saut.
        val gap = sched(med(FreqType.WEEKDAYS, weekdays = "7", times = "02:30", start = "2026-03-01"))
        assertEquals("2026-03-29T03:30", local(gap.next(at(2026, 3, 28, 12))!!))
        // Automne (25 octobre 2026) : 08:00 locale = 07:00 UTC ; 02:30 (deux fois) = la première.
        val autumn = sched(med(FreqType.EVERY_N_WEEKS, 1, times = "02:30,08:00", start = "2026-10-18"))
        val o = autumn.occurrencesBetween(at(2026, 10, 25, 0), at(2026, 10, 25, 23))
        assertEquals(listOf(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), Instant.parse("2026-10-25T07:00:00Z").toEpochMilli()), o)
    }

    @Test fun startAndEndBounds() {
        val s = sched(med(FreqType.EVERY_N_DAYS, 2, end = "2026-03-05"))
        assertEquals(at(2026, 3, 5, 8), s.next(at(2026, 3, 4, 8)))
        assertNull(s.next(at(2026, 3, 5, 8)))
        assertEquals(at(2026, 3, 5, 8), s.previous(at(2026, 4, 1, 0)))
        assertEquals(3, s.occurrencesBetween(at(2026, 2, 1, 0), at(2026, 4, 1, 0)).size)
    }

    @Test fun emptyStartAnchorsOnToday() {
        val s = Schedule.of(med(FreqType.EVERY_N_DAYS, 3, start = ""), z, today)
        assertEquals("2026-11-29T08:00", local(s.next(at(2026, 11, 26, 9))!!))
        assertEquals("2026-11-23T08:00", local(s.previous(at(2026, 11, 26, 7))!!))
    }

    @Test fun normalizationAndStock() {
        assertEquals(FreqType.DAILY, med(FreqType.WEEKDAYS, weekdays = "").normalizedFrequency().freqType)
        assertEquals(FreqType.DAILY, med(FreqType.EVERY_N_DAYS, 1).normalizedFrequency().freqType)
        assertEquals("", med(FreqType.EVERY_N_MONTHS, 3, weekdays = "1,2").normalizedFrequency().weekdays)
        assertEquals(1, med(FreqType.DAILY, 4).normalizedFrequency().freqInterval)
        // Stock : 3 comprimés pour un traitement mensuel ≈ 91 jours ; lun/mer/ven 2 fois par jour : 6 prises par semaine.
        assertEquals(91, Stock.daysLeft(3.0, 1.0, sched(med(FreqType.EVERY_N_MONTHS, 1))))
        assertEquals(14, Stock.daysLeft(12.0, 1.0, sched(med(FreqType.WEEKDAYS, weekdays = "1,3,5", times = "08:00,20:00"))))
        assertEquals(30, Stock.daysLeft(120.0, 2.0, sched(med(FreqType.DAILY, times = "08:00,20:00"))))
    }

    @Test fun monthlyDoseGuardAndLateCheck() {
        val s = sched(med(FreqType.EVERY_N_MONTHS, 1, times = "09:00", start = "2026-01-12"))
        val now = at(2026, 3, 12, 9, 10)
        val window = DoseGuard.currentWindow(s, now)
        // Fenêtre de mi-chemin entre le 12 février et le 12 mars (28 jours) : environ deux semaines.
        assertEquals(at(2026, 2, 26, 9), window.first)
        // Une prise faite il y a 3 jours couvre la prise du mois : la nouvelle demande confirmation.
        assertNotNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(2026, 3, 9, 10), "")), window))
        // Celle du mois précédent ne compte pas.
        assertNull(DoseGuard.alreadyGiven(listOf(TakenDose(at(2026, 2, 12, 9), "")), window))
        // Pas de relance « En retard » si la prise a été faite en avance.
        val sched = at(2026, 3, 12, 9)
        assertFalse(LateCheck.shouldAlert(s, 30, true, sched, listOf(HandledDose(at(2026, 3, 9, 10), null)), at(2026, 3, 12, 9, 30)))
        assertTrue(LateCheck.shouldAlert(s, 30, true, sched, emptyList(), at(2026, 3, 12, 9, 30)))
    }

    @Test fun weeklyAdherence() {
        val s = sched(med(FreqType.WEEKDAYS, weekdays = "1,3,5", start = "2026-03-01"))
        assertEquals(6, Adherence.expected(s, at(2026, 3, 2, 0), at(2026, 3, 15, 23, 59)))
        // Mensuel : une prise par mois sur un trimestre.
        assertEquals(3, Adherence.expected(sched(med(FreqType.EVERY_N_MONTHS, 1, start = "2026-01-12")), at(2026, 1, 1, 0), at(2026, 3, 31, 23)))
    }
}
