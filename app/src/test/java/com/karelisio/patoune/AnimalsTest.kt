package com.karelisio.patoune

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.AnimalOps
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PatouneDb
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnimalsTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PatouneDb::class.java)

    @Test fun migration1To2KeepsAnimals() {
        helper.createDatabase("migr.db", 1).use { db ->
            db.execSQL("INSERT INTO animals (id, name, species, breed, sex, chipNumber, sterilized, vetName, vetPhone, emergencyVetName, emergencyVetPhone, allergies, history, breathThreshold, createdBy, updatedAt, deleted) " +
                "VALUES ('a1', 'Mochi', 'Chat', '', '', '', 0, '', '', '', '', '', '', 30, '', 1, 0)")
        }
        helper.runMigrationsAndValidate("migr.db", 2, true).use { db ->
            db.query("SELECT name, sortOrder FROM animals WHERE id = 'a1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Mochi", c.getString(0))
                assertEquals(0, c.getInt(1))
            }
        }
    }

    @Test fun deletingAnimalDeletesItsTreatments() = runTest {
        val d = TestDevice("A")
        val cat = d.store.save(Animal(name = "Mochi"))
        val dog = d.store.save(Animal(name = "Rex"))
        d.store.save(Medication(animalId = cat.id, name = "Flixotide"))
        d.store.save(Medication(animalId = dog.id, name = "Autre"))
        val removed = AnimalOps.delete(d.store, d.db, cat)
        assertEquals(listOf("Flixotide"), removed.map { it.name })
        assertTrue(d.db.animals().byId(cat.id)!!.deleted)
        assertEquals(listOf("Autre"), d.db.medications().allRows().filter { !it.deleted }.map { it.name })
    }

    @Test fun reorderRewritesOnlyChangedPositions() {
        val list = listOf("A", "B", "C").mapIndexed { i, n -> Animal(id = n, name = n, sortOrder = i) }
        val changed = AnimalOps.reorder(list, 2, 0)
        assertEquals(mapOf("C" to 0, "A" to 1, "B" to 2), changed.associate { it.id to it.sortOrder })
        assertTrue(AnimalOps.reorder(list, 0, 0).isEmpty())
    }
}
