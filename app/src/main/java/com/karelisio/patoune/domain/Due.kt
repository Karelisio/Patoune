package com.karelisio.patoune.domain

/** Un traitement est-il « à donner » maintenant (accueil, bouton « Donné ») ? Logique pure, sans Android. */
object Due {
    /** Horizon : une prise prévue dans les 12 h est à donner. */
    const val SOON_MS = 12 * 3_600_000L
    /** Une prise faite il y a moins de 2 min garde la carte le temps de son animation de repli. */
    const val RECENT_MS = 2 * 60_000L

    /**
     * [givenAt] : prise donnée dans la fenêtre en cours du garde-fou double dose (ou null) ;
     * [nextAt] : prochaine occurrence à donner (voir [nextOpen] ; null = traitement terminé) ; [currentAt] : dernière occurrence prévue
     * avant [now] (null = pas encore commencé). Dû si :
     * - une prise vient d'être faite (moins de [RECENT_MS]) : la carte joue son animation de repli ;
     * - sinon, si la fenêtre en cours n'a pas de prise et que le traitement n'est pas terminé : la prochaine
     *   prise tombe dans les [SOON_MS], ou l'occurrence courante est passée (prise oubliée, même espacée).
     * Une prise déjà faite dans la fenêtre en cours rend le traitement « à venir » : « Donné » ouvrirait
     * de toute façon « Déjà donné ? ».
     */
    /**
     * Prochaine occurrence après [after] qui reste à donner : on saute celles dont la fenêtre du garde-fou contient
     * déjà la prise [givenAt] (prise un peu en avance, ou faite après l'horaire), pour ne pas afficher une échéance
     * que « Donné » refuserait. Au plus [MAX_SKIP] sauts (un par horaire de la journée).
     */
    fun nextOpen(schedule: Schedule, after: Long, givenAt: Long?): Long? {
        var next = schedule.next(after) ?: return null
        if (givenAt == null) return next
        repeat(MAX_SKIP) {
            if (givenAt < DoseGuard.currentWindow(schedule, next).first) return next
            next = schedule.next(next) ?: return null
        }
        return next
    }

    private const val MAX_SKIP = 24

    fun isDue(nextAt: Long?, currentAt: Long?, givenAt: Long?, now: Long): Boolean {
        if (givenAt != null) return now - givenAt < RECENT_MS
        if (nextAt == null) return false
        return nextAt - now <= SOON_MS || (currentAt != null && currentAt <= now)
    }
}
