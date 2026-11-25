package com.karelisio.patoune.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class TakenDose(val takenAt: Long, val by: String)

object DoseGuard {
    /**
     * Fenêtre en cours : de l'horaire prévu le plus proche avant [now] (moins une marge)
     * jusqu'au prochain horaire. Pour un traitement « si besoin », 4 h glissantes.
     */
    fun currentWindow(times: List<LocalTime>, now: Long, zone: ZoneId, asNeeded: Boolean = false): LongRange {
        if (asNeeded || times.isEmpty()) return (now - 4 * 3_600_000L)..now
        val sorted = times.sorted()
        val z = Instant.ofEpochMilli(now).atZone(zone)
        val candidates = (-1..1).flatMap { d -> sorted.map { t -> ZonedDateTime.of(z.toLocalDate().plusDays(d.toLong()), t, zone) } }
            .map { it.toInstant().toEpochMilli() }.sorted()
        val nextIdx = candidates.indexOfFirst { it > now + MARGIN }
        val next = candidates[nextIdx]
        val prev = candidates[nextIdx - 1]
        // On ouvre la fenêtre à mi-chemin entre l'horaire précédent et celui-ci pour couvrir une prise un peu en avance.
        val start = prev - (prev - candidates.getOrElse(nextIdx - 2) { prev - 12 * 3_600_000L }) / 2
        return start..minOf(now, next)
    }

    /** Retourne la prise déjà enregistrée dans la fenêtre, s'il y en a une. */
    fun alreadyGiven(doses: List<TakenDose>, window: LongRange): TakenDose? =
        doses.filter { it.takenAt in window }.maxByOrNull { it.takenAt }

    private const val MARGIN = 30 * 60_000L
}
