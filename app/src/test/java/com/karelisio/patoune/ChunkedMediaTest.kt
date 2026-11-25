package com.karelisio.patoune

import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.MediaItem
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.sync.Envelope
import com.karelisio.patoune.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Médias volumineux (format v2) : envoi par morceaux chiffrés, reprise après coupure, ancien format toujours lisible. */
@RunWith(RobolectricTestRunner::class)
class ChunkedMediaTest {
    private val server = FakeServer()

    /** Contenu reconnaissable : s'il arrivait en clair au serveur, on le verrait. */
    private fun video(size: Int): ByteArray {
        val pattern = "Mochi tousse fort, filmé par Camille. ".toByteArray()
        return ByteArray(size) { pattern[it % pattern.size] }
    }

    private suspend fun addVideo(d: TestDevice, id: String, bytes: ByteArray): MediaItem {
        val cat = d.store.save(Animal(name = "Mochi"))
        val f = File(d.mediaDir, id).apply { writeBytes(bytes) }
        return d.store.save(MediaItem(id = id, animalId = cat.id, kind = MediaKind.VIDEO, mime = "video/mp4", sizeBytes = bytes.size.toLong(), localPath = f.absolutePath))
    }

    @Test fun largeVideoResumesAfterCutAndArrivesIdentical() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("Camille", "https://fake")
        val bytes = video(3 * 1024 * 1024 + 512 * 1024) // 3,5 Mio → 4 morceaux
        addVideo(a, "video-3m5", bytes)

        // Coupure au 3ᵉ morceau : la sync échoue, deux morceaux sont confirmés et notés hors base.
        server.failPart = { id, index -> id == "video-3m5" && index == 2 }
        assertTrue(runCatching { a.sync!!.sync() }.isFailure)
        assertEquals(listOf(0, 1), server.partUploads.filter { it.first == "video-3m5" }.map { it.second })
        assertEquals("${bytes.size} 4 2", File(a.progressDir, "video-3m5").readText())
        assertFalse(a.db.media().byId("video-3m5")!!.uploaded)

        // Reprise : seuls les morceaux manquants partent, puis le média est terminé.
        a.sync!!.sync()
        assertEquals(listOf(0, 1, 2, 3), server.partUploads.filter { it.first == "video-3m5" }.map { it.second })
        assertTrue(a.db.media().byId("video-3m5")!!.uploaded)
        assertFalse(File(a.progressDir, "video-3m5").exists())
        val hid = a.sessions.load()!!.householdId
        assertEquals(4, server.completed[hid to "video-3m5"]!!.parts)
        assertEquals(SyncEngine.encryptedSize(bytes.size.toLong()), server.completed[hid to "video-3m5"]!!.size)

        // B reçoit un fichier identique, sans fichier temporaire, et ne le renvoie pas.
        val b = TestDevice("B", server = server)
        b.household!!.join(a.household.invite(), "Alex")
        b.sync!!.sync()
        assertArrayEquals(bytes, File(b.mediaDir, "video-3m5").readBytes())
        assertFalse(File(b.mediaDir, "video-3m5.download").exists())
        assertTrue(b.db.media().byId("video-3m5")!!.uploaded)
        b.sync.sync(); a.sync.sync()
        assertEquals(4, server.partUploads.count { it.first == "video-3m5" })

        // Le serveur n'a reçu aucun octet en clair.
        val seen = server.received.toString()
        for (w in listOf("Mochi", "tousse", "Camille")) assertFalse(w, seen.contains(w))
    }

    @Test fun reorderedPartsAreRejected() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("Camille", "https://fake")
        addVideo(a, "video-2m", video(2 * 1024 * 1024))
        a.sync!!.sync()
        // Serveur malveillant : il échange deux morceaux. L'AAD (index, total) le détecte.
        val hid = a.sessions.load()!!.householdId
        val p0 = server.parts[Triple(hid, "video-2m", 0)]!!
        server.parts[Triple(hid, "video-2m", 0)] = server.parts[Triple(hid, "video-2m", 1)]!!
        server.parts[Triple(hid, "video-2m", 1)] = p0

        val b = TestDevice("B", server = server)
        b.household!!.join(a.household.invite(), "Alex")
        b.sync!!.sync()
        assertFalse(File(b.mediaDir, "video-2m").exists())
        assertFalse(File(b.mediaDir, "video-2m.download").exists())
        assertNull(b.db.media().byId("video-2m")!!.localPath)
    }

    @Test fun legacySingleBlobMediaIsStillReadable() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("Camille", "https://fake")
        val bytes = video(300_000)
        // Média envoyé par une ancienne version (un seul blob, AAD householdId|media|id).
        val s = a.sessions.load()!!
        val legacy = server.client { runBlocking { a.sessions.load()?.token } }
        legacy.putMedia("photo-old", Envelope.seal(s.key, s.epoch, bytes, SyncEngine.aad(s, "media", "photo-old"), compress = false))
        val cat = a.store.save(Animal(name = "Mochi"))
        a.store.save(MediaItem(id = "photo-old", animalId = cat.id, uploaded = true, sizeBytes = bytes.size.toLong()))
        a.sync!!.sync()
        assertEquals(0, server.partUploads.size)

        val b = TestDevice("B", server = server)
        b.household!!.join(a.household.invite(), "Alex")
        b.sync!!.sync()
        assertArrayEquals(bytes, File(b.mediaDir, "photo-old").readBytes())
        assertArrayEquals(bytes, File(a.mediaDir, "photo-old").readBytes())
        assertEquals(0, server.partUploads.size)
    }
}
