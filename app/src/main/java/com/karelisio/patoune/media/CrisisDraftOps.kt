package com.karelisio.patoune.media

import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.domain.CrisisDraft

/** Écritures liées au brouillon de crise (décisions dans [CrisisDraft]). */
object CrisisDraftOps {
    /**
     * Retour de la caméra vidéo ouverte à [since] : si une vidéo a été rattachée au brouillon pendant ce passage,
     * la crise est enregistrée (heure du tap conservée, durée de la vidéo si non renseignée). Sinon null : rien n'est écrit.
     */
    suspend fun afterCamera(store: Store, db: PatouneDb, draft: Crisis, animalId: String, saved: Boolean, since: Long): Crisis? {
        val videos = db.media().attached(draft.id).filter { it.kind == MediaKind.VIDEO && it.takenAt >= since }
        if (!CrisisDraft.shouldAutoSave(saved, videos.size)) return null
        return store.save(draft.copy(animalId = draft.animalId.ifEmpty { animalId },
            durationSec = CrisisDraft.durationSec(draft.durationSec, videos.map { it.durationMs })))
    }

    /** Fiche quittée sans enregistrer : les médias d'un brouillon jamais enregistré sont supprimés (fichiers compris). */
    suspend fun leave(db: PatouneDb, media: MediaRepo, draftId: String, saved: Boolean): CrisisDraft.Leave {
        val attached = db.media().attached(draftId)
        val decision = CrisisDraft.onLeave(saved, attached.size)
        if (decision == CrisisDraft.Leave.DISCARD_MEDIA) attached.forEach { media.delete(it) }
        return decision
    }
}
