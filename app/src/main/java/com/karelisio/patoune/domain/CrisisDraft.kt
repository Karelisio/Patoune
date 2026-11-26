package com.karelisio.patoune.domain

/**
 * Brouillon de crise : rien n'est enregistré tant que l'utilisateur n'a pas touché « Enregistrer »
 * ou rapporté une vidéo de la caméra (une crise filmée est une vraie crise). Sinon, une crise vide
 * fausserait l'analyse (fréquence, intensité, PDF).
 */
object CrisisDraft {
    enum class Leave {
        /** La crise existe (déjà en base ou auto-enregistrée) : on garde tout. */
        KEEP,
        /** Brouillon jamais enregistré : ses médias (photos importées ou prises) sont supprimés. */
        DISCARD_MEDIA,
    }

    /** Retour de la caméra vidéo : enregistrement automatique si le brouillon n'existe pas encore et qu'une vidéo a été rapportée. */
    fun shouldAutoSave(saved: Boolean, mediaCount: Int): Boolean = !saved && mediaCount > 0

    /** Fiche quittée (retour système ou flèche) sans passer par « Enregistrer ». */
    fun onLeave(saved: Boolean, mediaCount: Int): Leave = if (!saved && mediaCount > 0) Leave.DISCARD_MEDIA else Leave.KEEP

    /** Push « Crise enregistrée » à l'enregistrement manuel : seulement pour une nouvelle crise pas encore annoncée. */
    fun shouldPushOnSave(isNew: Boolean, alreadyPushed: Boolean): Boolean = isNew && !alreadyPushed

    /** Durée (s) : celle saisie si renseignée, sinon celle de la première vidéo (0 si aucune). */
    fun durationSec(current: Int, videoDurationsMs: List<Long>): Int =
        if (current > 0) current else videoDurationsMs.firstOrNull { it > 0 }?.let { (it / 1000).toInt() } ?: 0
}
