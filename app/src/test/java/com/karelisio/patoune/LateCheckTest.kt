package com.karelisio.patoune

import com.karelisio.patoune.domain.HandledDose
import com.karelisio.patoune.domain.LateCheck
import com.karelisio.patoune.domain.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LateCheckTest {
    private val z = ZoneId.of("Europe/Paris")
    private val times = ReminderPlanner.parseTimes("08:00,20:00")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 3, d, h, m, 0, 0, z).toInstant().toEpochMilli()
    private val sched = at(3, 8)

    private fun alert(handled: List<HandledDose> = emptyList(), now: Long = at(3, 8, 30), late: Int = 30, active: Boolean = true) =
        LateCheck.shouldAlert(times, z, late, active, sched, handled, now)

    @Test fun checkInstantIsScheduledPlusDelay() {
        assertEquals(at(3, 8, 30), LateCheck.checkAt(sched, 30, now = sched))
        assertEquals(at(3, 9, 15), LateCheck.checkAt(sched, 75, now = at(3, 8, 20)))
    }

    @Test fun noCheckWhenDisabledOrAlreadyPast() {
        assertNull(LateCheck.checkAt(sched, 0, now = sched))
        assertNull(LateCheck.checkAt(sched, -5, now = sched))
        // Rappel reporté au-delà du délai : la vérification a déjà eu lieu.
        assertNull(LateCheck.checkAt(sched, 30, now = at(3, 8, 40)))
    }

    @Test fun alertsWhenNothingRecorded() {
        assertTrue(alert())
        // Une prise de la veille au soir ne couvre pas l'horaire du matin.
        assertTrue(alert(listOf(HandledDose(at(2, 20, 5), at(2, 20)))))
    }

    @Test fun noAlertWhenGivenOrSkipped() {
        assertFalse(alert(listOf(HandledDose(at(3, 8, 10), sched))))
        // Donné un peu en avance, sans horaire associé (bouton de l'appli).
        assertFalse(alert(listOf(HandledDose(at(3, 7, 40), null))))
        // Sauté pour cet horaire.
        assertFalse(alert(listOf(HandledDose(at(3, 8, 5), sched))))
    }

    @Test fun noAlertWhenDisabledInactiveOrNextDoseArrived() {
        assertFalse(alert(late = 0))
        assertFalse(alert(active = false))
        assertFalse(LateCheck.shouldAlert(emptyList(), z, 30, true, sched, emptyList(), at(3, 8, 30)))
        // L'horaire de 20:00 est arrivé : son propre rappel prend le relais.
        assertFalse(alert(now = at(3, 20, 1), late = 721))
    }
}
