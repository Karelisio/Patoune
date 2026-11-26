package com.karelisio.patoune.domain

import java.time.LocalDate
import kotlin.math.floor

object Stock {
    fun decrement(remaining: Double?, unitsPerDose: Double): Double? =
        remaining?.let { maxOf(0.0, it - unitsPerDose) }

    fun increment(remaining: Double?, unitsPerDose: Double): Double? = remaining?.let { it + unitsPerDose }

    fun isLow(remaining: Double?, alertAt: Double?): Boolean =
        remaining != null && alertAt != null && remaining <= alertAt

    /** Nombre de jours restants selon le nombre de prises quotidiennes. */
    fun daysLeft(remaining: Double?, unitsPerDose: Double, dosesPerDay: Int): Int? =
        daysLeft(remaining, unitsPerDose, dosesPerDay.toDouble())

    /** Nombre de jours restants selon le nombre moyen de prises par jour (ex. ~1/30 pour un traitement mensuel). */
    fun daysLeft(remaining: Double?, unitsPerDose: Double, dosesPerDay: Double): Int? {
        if (remaining == null || dosesPerDay <= 0 || unitsPerDose <= 0) return null
        return floor(remaining / (unitsPerDose * dosesPerDay) + 1e-9).toInt()
    }

    /** Jours restants selon le calendrier du traitement (prises par jour en moyenne sur un cycle). */
    fun daysLeft(remaining: Double?, unitsPerDose: Double, schedule: Schedule): Int? =
        daysLeft(remaining, unitsPerDose, schedule.dosesPerDay())

    /** Rappel de renouvellement d'ordonnance : 7 jours avant la date ou la fin estimée du stock. */
    fun renewalDue(renewal: LocalDate?, today: LocalDate, daysLeft: Int?): Boolean =
        (renewal != null && !today.isBefore(renewal.minusDays(7))) || (daysLeft != null && daysLeft <= 7)
}
