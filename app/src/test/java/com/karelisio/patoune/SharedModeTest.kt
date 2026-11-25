package com.karelisio.patoune

import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.MediaItem
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.Syncable
import com.karelisio.patoune.data.SyncMode
import com.karelisio.patoune.domain.DoseGuard
import com.karelisio.patoune.domain.ReminderPlanner
import com.karelisio.patoune.domain.TakenDose
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SharedModeTest {
    private val server = FakeServer()

    private suspend fun content(d: TestDevice): Map<String, Any> {
        fun strip(r: Syncable): Syncable = when (r) {
            is MediaItem -> r.copy(localPath = null, uploaded = false)
            else -> r
        }
        return d.store.adapters.associate { a -> a.name to a.dao.allRows().map(::strip).sortedBy { it.id } }
    }

    @Test fun soloToSharedMigrationLosesNothing() = runTest {
        val a = TestDevice("A", server = server)
        // Données créées en solo, sans aucun appel au serveur.
        val cat = a.store.save(Animal(name = "Mochi", breathThreshold = 30, allergies = "Pollens"))
        val med = a.store.save(Medication(animalId = cat.id, name = "Flixotide", stockRemaining = 120.0, times = "08:00,20:00"))
        a.store.save(DoseLog(medicationId = med.id, animalId = cat.id, takenAt = 1_000))
        a.store.save(BreathCount(animalId = cat.id, at = 2_000, taps = 12, durationSec = 30, bpm = 24.0))
        File(a.mediaDir, "video-0001").writeBytes(ByteArray(4096) { (it % 7).toByte() })
        a.store.save(MediaItem(id = "video-0001", animalId = cat.id, kind = MediaKind.VIDEO, localPath = File(a.mediaDir, "video-0001").absolutePath))
        assertEquals(0, server.blobs.size)

        a.household!!.create("Camille", "https://fake")
        assertEquals(SyncMode.SHARED, a.settings.current().mode)

        val b = TestDevice("B", server = server)
        b.household!!.join(a.household.invite(), "Alex")
        b.sync!!.sync(); a.sync!!.sync()

        val ca = content(a); val cb = content(b)
        assertEquals(ca, cb)
        assertEquals("Mochi", b.db.animals().byId(cat.id)!!.name)
        assertArrayEquals(File(a.mediaDir, "video-0001").readBytes(), File(b.mediaDir, "video-0001").readBytes())
        assertEquals(setOf("Camille", "Alex"), b.db.members().allRows().map { it.name }.toSet())

        // Le serveur n'a reçu que des blobs chiffrés.
        val seen = server.received.toString()
        for (w in listOf("Mochi", "Flixotide", "Pollens", "Camille", "Alex", "medication")) assertFalse(w, seen.contains(w))
    }

    @Test fun doseGivenOnOnePhoneAppearsOnOthersAndSilencesTheirReminder() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("Camille", "https://fake")
        val cat = a.store.save(Animal(name = "Mochi"))
        val med = a.store.save(Medication(animalId = cat.id, name = "Flixotide", times = "08:00,20:00"))
        a.sync!!.sync()
        val b = TestDevice("B", server = server); b.household!!.join(a.household.invite(), "Alex")
        val c = TestDevice("C", server = server); c.household!!.join(a.household.invite(), "Sam")

        val now = System.currentTimeMillis()
        val window = DoseGuard.currentWindow(ReminderPlanner.parseTimes(med.times), now, ZoneId.systemDefault())
        fun given(d: TestDevice): TakenDose? = kotlinx.coroutines.runBlocking {
            DoseGuard.alreadyGiven(d.db.doses().since(med.id, window.first).filter { it.status == DoseStatus.GIVEN }.map { TakenDose(it.takenAt, it.createdBy) }, window)
        }
        assertNull(given(b))

        b.store.save(DoseLog(medicationId = med.id, animalId = cat.id, takenAt = now, status = DoseStatus.GIVEN))
        b.sync!!.sync(); a.sync.sync(); c.sync!!.sync()

        // Le rappel ne se déclenche plus chez A et C, et l'on sait qui a donné.
        assertEquals("Alex", given(a)!!.by)
        assertEquals("Alex", given(c)!!.by)
        assertEquals(content(a), content(c))
    }

    @Test fun concurrentEditsOnThreeDevicesConverge() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("A", "https://fake")
        val cat = a.store.save(Animal(name = "Mochi", breathThreshold = 30))
        a.sync!!.sync()
        val b = TestDevice("B", server = server); b.household!!.join(a.household.invite(), "B")
        val c = TestDevice("C", server = server); c.household!!.join(a.household.invite(), "C")

        // Hors ligne : chacun modifie un ou plusieurs champs du même animal.
        a.store.save(a.db.animals().byId(cat.id)!!.copy(name = "Mochi A", breathThreshold = 28))
        b.store.save(b.db.animals().byId(cat.id)!!.copy(name = "Mochi B"))
        c.store.save(c.db.animals().byId(cat.id)!!.copy(allergies = "Acariens"))
        c.store.delete(c.store.save(Medication(animalId = cat.id, name = "Temporaire")))

        for (round in 0 until 2) for (d in listOf(c, a, b)) d.sync!!.sync()
        val ca = content(a)
        assertEquals(ca, content(b)); assertEquals(ca, content(c))
        val final = a.db.animals().byId(cat.id)!!
        assertEquals("Acariens", final.allergies)
        assertEquals(28, final.breathThreshold)
        assertTrue(a.db.medications().allRows().single().deleted)
    }

    @Test fun leavingHouseholdKeepsFullLocalCopy() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("Camille", "https://fake")
        val cat = a.store.save(Animal(name = "Mochi"))
        File(a.mediaDir, "photo-0001").writeBytes(byteArrayOf(1, 2, 3, 4))
        a.store.save(MediaItem(id = "photo-0001", animalId = cat.id, localPath = File(a.mediaDir, "photo-0001").absolutePath))
        a.sync!!.sync()
        val b = TestDevice("B", server = server)
        b.household!!.join(a.household.invite(), "Alex")
        // B n'a pas encore téléchargé le média : la sortie le rapatrie d'abord.
        b.store.saveLocal(b.db.media().byId("photo-0001")!!.copy(localPath = null)); File(b.mediaDir, "photo-0001").delete()
        val before = content(b)

        b.household.leave()
        val s = b.settings.current()
        assertEquals(SyncMode.SOLO, s.mode)
        assertNull(s.householdId)
        assertNull(b.sessions.load())
        assertEquals(before, content(b))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(b.mediaDir, "photo-0001").readBytes())
        assertEquals(1, server.members.size)

        // En solo, les écritures restent locales.
        val blobs = server.blobs.size
        b.store.save(Animal(name = "Nouveau"))
        assertNull(b.sync!!.sync())
        assertEquals(blobs, server.blobs.size)
    }

    @Test fun removedMemberLosesAccessAndKeyRotates() = runTest {
        val a = TestDevice("A", server = server)
        a.household!!.create("Camille", "https://fake")
        val b = TestDevice("B", server = server); b.household!!.join(a.household.invite(), "Alex")
        val c = TestDevice("C", server = server); c.household!!.join(a.household.invite(), "Sitter")
        val oldKey = a.sessions.load()!!.key
        val cId = c.sessions.load()!!.memberId

        a.household.remove(cId)
        assertEquals(2, a.sessions.load()!!.epoch)
        assertFalse(oldKey.contentEquals(a.sessions.load()!!.key))

        a.store.save(Animal(name = "Après retrait"))
        a.sync!!.sync(); b.sync!!.sync()
        assertEquals(2, b.sessions.load()!!.epoch)
        assertArrayEquals(a.sessions.load()!!.key, b.sessions.load()!!.key)
        assertNotNull(b.db.animals().allRows().firstOrNull { it.name == "Après retrait" })
        assertTrue(runCatching { c.sync!!.sync() }.isFailure)
        assertTrue(a.db.members().byId(cId)!!.deleted)
    }
}
