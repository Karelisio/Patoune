package com.karelisio.patoune

import com.karelisio.patoune.backup.Backup
import com.karelisio.patoune.backup.WrongPassword
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.MediaItem
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.data.Medication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun exportImportRoundTripWithMedia() = runTest {
        val dirA = tmp.newFolder("a"); val dirB = tmp.newFolder("b")
        val a = TestDevice("A")
        val cat = a.store.save(Animal(name = "Mochi", breathThreshold = 28))
        a.store.save(Medication(animalId = cat.id, name = "Flixotide", stockRemaining = 120.0))
        val photo = File(dirA, "m1").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        a.store.save(MediaItem(id = "m1", animalId = cat.id, kind = MediaKind.PHOTO, localPath = photo.absolutePath))

        val out = ByteArrayOutputStream()
        Backup(a.db, a.store) { dirA }.export(out, "motdepasse".toCharArray(), iterations = 1000)
        val zip = out.toByteArray()
        check(!String(zip, Charsets.ISO_8859_1).contains("Mochi")) { "données en clair dans la sauvegarde" }

        val b = TestDevice("B")
        val res = Backup(b.db, b.store) { dirB }.import(ByteArrayInputStream(zip), "motdepasse".toCharArray())
        assertEquals(1, res.media)
        assertEquals("Mochi", b.db.animals().byId(cat.id)!!.name)
        assertEquals(28, b.db.animals().byId(cat.id)!!.breathThreshold)
        assertEquals(120.0, b.db.medications().allRows().single().stockRemaining!!, 0.0)
        assertArrayEquals(photo.readBytes(), File(dirB, "m1").readBytes())
        assertEquals(File(dirB, "m1").absolutePath, b.db.media().byId("m1")!!.localPath)

        assertThrows(WrongPassword::class.java) {
            kotlinx.coroutines.runBlocking { Backup(b.db, b.store) { dirB }.import(ByteArrayInputStream(zip), "mauvais".toCharArray()) }
        }
    }
}
