package com.karelisio.patoune.domain

import java.time.LocalTime
import java.time.ZoneId

/** Prise enregistrée (donnée ou sautée), vue par la vérification de retard. */
data class HandledDose(val takenAt: Long, val scheduledAt: Long?)

/** Prise en retard : quand vérifier, et faut-il relancer. Logique pure, sans Android. */
object LateCheck {
    /**
     * Instant de la vérification pour une prise prévue à [scheduledAt], ou null si aucune relance
     * n'est prévue ([lateMinutes] ≤ 0) ou si cet instant est déjà passé (rappel reporté au-delà du délai).
     */
    fun checkAt(scheduledAt: Long, lateMinutes: Int, now: Long): Long? {
        if (lateMinutes <= 0) return null
        return (scheduledAt + lateMinutes * 60_000L).takeIf { it > now }
    }

    /**
     * Faut-il signaler la prise prévue à [scheduledAt] comme en retard ?
     * Non si le traitement n'a plus de rappel ([active] faux : supprimé ou « si besoin »), si le délai vaut 0,
     * si une prise donnée ou sautée couvre cet horaire (même fenêtre que le garde-fou double dose),
     * ou si l'horaire suivant est déjà arrivé (son propre rappel prend le relais).
     */
    fun shouldAlert(
        times: List<LocalTime>,
        zone: ZoneId,
        lateMinutes: Int,
        active: Boolean,
        scheduledAt: Long,
        handled: List<HandledDose>,
        now: Long,
    ): Boolean {
        if (!active || lateMinutes <= 0 || times.isEmpty()) return false
        val next = ReminderPlanner.next(times, scheduledAt, zone, null, null)
        if (next != null && now >= next) return false
        val windowStart = DoseGuard.currentWindow(times, scheduledAt, zone).first
        return handled.none { it.scheduledAt == scheduledAt || it.takenAt in windowStart..now }
    }
}
