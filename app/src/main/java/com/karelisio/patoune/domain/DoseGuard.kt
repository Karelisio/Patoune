package com.karelisio.patoune.domain

import java.time.LocalTime
import java.time.ZoneId

data class TakenDose(val takenAt: Long, val by: String)

object DoseGuard {
    /** Fenêtre en cours pour des horaires quotidiens (ancienne API). */
    fun currentWindow(times: List<LocalTime>, now: Long, zone: ZoneId, asNeeded: Boolean = false): LongRange =
        currentWindow(Schedule.daily(times, zone), now, asNeeded)

    /**
     * Fenêtre en cours : de mi-chemin entre l'occurrence précédente et l'occurrence courante (la dernière
     * prévue avant [now] + une marge d'avance de 30 min) jusqu'à [now]. Pour un traitement mensuel, la fenêtre
     * couvre donc environ deux semaines. Les dates de début et de fin sont ignorées : une prise juste avant le
     * début ou après la fin reste protégée. Pour un traitement « si besoin », 4 h glissantes.
     */
    fun currentWindow(schedule: Schedule, now: Long, asNeeded: Boolean = false): LongRange {
        if (asNeeded || schedule.times.isEmpty()) return (now - 4 * 3_600_000L)..now
        val s = schedule.unbounded()
        val current = s.previous(now + MARGIN + 1) ?: return (now - 4 * 3_600_000L)..now
        val prev = s.previous(current) ?: (current - 12 * 3_600_000L)
        // On ouvre la fenêtre à mi-chemin entre l'occurrence précédente et celle-ci pour couvrir une prise un peu en avance.
        return (current - (current - prev) / 2)..now
    }

    /** Retourne la prise déjà enregistrée dans la fenêtre, s'il y en a une. */
    fun alreadyGiven(doses: List<TakenDose>, window: LongRange): TakenDose? =
        doses.filter { it.takenAt in window }.maxByOrNull { it.takenAt }

    private const val MARGIN = 30 * 60_000L
}
