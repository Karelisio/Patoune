package com.karelisio.patoune

import androidx.test.core.app.ApplicationProvider
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.domain.CrisisDraft
import com.karelisio.patoune.domain.CrisisDraft.Leave
import com.karelisio.patoune.media.CrisisDraftOps
import com.karelisio.patoune.media.MediaRepo
import com.karelisio.patoune.domain.newId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Décisions pures du brouillon de crise. */
class CrisisDraftDecisionsTest {
    @Test fun cameraCancelledSavesNothing() {
        assertFalse(CrisisDraft.shouldAutoSave(saved = false, mediaCount = 0))
        assertEquals(Leave.KEEP, CrisisDraft.onLeave(saved = false, mediaCount = 0))
    }

    @Test fun videoAutoSavesOnceAndIsKeptOnLeave() {
        assertTrue(CrisisDraft.shouldAutoSave(saved = false, mediaCount = 1))
        // Déjà enregistrée (auto ou fiche existante) : pas de second enregistrement automatique.
        assertFalse(CrisisDraft.shouldAutoSave(saved = true, mediaCount = 2))
        assertEquals(Leave.KEEP, CrisisDraft.onLeave(saved = true, mediaCount = 1))
    }

    @Test fun manualSavePushesOnlyForNewCrisisNotAlreadyAnnounced() {
        assertTrue(CrisisDraft.shouldPushOnSave(isNew = true, alreadyPushed = false))
        assertFalse(CrisisDraft.shouldPushOnSave(isNew = true, alreadyPushed = true))
        assertFalse(CrisisDraft.shouldPushOnSave(isNew = false, alreadyPushed = false))
    }

    @Test fun abandonedDraftWithImportedPhotoDiscardsMedia() {
        assertEquals(Leave.DISCARD_MEDIA, CrisisDraft.onLeave(saved = false, mediaCount = 1))
    }

    @Test fun durationFromVideoOnlyWhenEmpty() {
        assertEquals(42, CrisisDraft.durationSec(0, listOf(0, 42_900)))
        assertEquals(90, CrisisDraft.durationSec(90, listOf(42_000)))
        assertEquals(0, CrisisDraft.durationSec(0, emptyList()))
    }
}

/** Écritures du brouillon sur une vraie base (Room en mémoire) et de vrais fichiers. */
@RunWith(RobolectricTestRunner::class)
class CrisisDraftOpsTest {
    private val dev = TestDevice("d1")
    private val media = MediaRepo(ApplicationProvider.getApplicationContext(), dev.store)
    private val draft = Crisis(id = newId(), animalId = "a1", startAt = 1_000L, types = "Toux")

    private suspend fun attach(kind: MediaKind, takenAt: Long, durationMs: Long = 0) =
        media.newFile().let { (id, f) ->
            f.writeBytes(byteArrayOf(1, 2, 3))
            media.register(id, "a1", kind, if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg", AttachType.CRISIS, draft.id, durationMs, takenAt)
        }

    @Test fun cameraCancelledWritesNoCrisis() = runTest {
        assertNull(CrisisDraftOps.afterCamera(dev.store, dev.db, draft, "a1", saved = false, since = 5_000L))
        assertTrue(dev.db.crises().allRows().isEmpty())
    }

    @Test fun videoBroughtBackSavesCrisisWithTapTimeAndVideoDuration() = runTest {
        attach(MediaKind.VIDEO, takenAt = 6_000L, durationMs = 37_000L)
        val saved = CrisisDraftOps.afterCamera(dev.store, dev.db, draft, "a1", saved = false, since = 5_000L)
        assertNotNull(saved)
        val row = dev.db.crises().byId(draft.id)!!
        assertEquals(1_000L, row.startAt)
        assertEquals(37, row.durationSec)
        // Déjà auto-enregistrée : un nouveau retour de caméra ne réenregistre rien ; quitter garde la vidéo.
        assertNull(CrisisDraftOps.afterCamera(dev.store, dev.db, draft, "a1", saved = true, since = 5_000L))
        assertEquals(Leave.KEEP, CrisisDraftOps.leave(dev.db, media, draft.id, saved = true))
        assertEquals(1, dev.db.media().attached(draft.id).size)
    }

    @Test fun photoOrOlderVideoDoesNotAutoSave() = runTest {
        attach(MediaKind.PHOTO, takenAt = 6_000L)
        attach(MediaKind.VIDEO, takenAt = 4_000L, durationMs = 10_000L) // importée avant d'ouvrir la caméra
        assertNull(CrisisDraftOps.afterCamera(dev.store, dev.db, draft, "a1", saved = false, since = 5_000L))
        assertTrue(dev.db.crises().allRows().isEmpty())
    }

    @Test fun abandonedDraftDeletesImportedMediaAndFiles() = runTest {
        val photo = attach(MediaKind.PHOTO, takenAt = 6_000L)
        assertTrue(media.file(photo.id).exists())
        assertEquals(Leave.DISCARD_MEDIA, CrisisDraftOps.leave(dev.db, media, draft.id, saved = false))
        assertTrue(dev.db.media().attached(draft.id).isEmpty())
        assertTrue(dev.db.media().byId(photo.id)!!.deleted)
        assertFalse(media.file(photo.id).exists())
        assertTrue(dev.db.crises().allRows().isEmpty())
    }
}
