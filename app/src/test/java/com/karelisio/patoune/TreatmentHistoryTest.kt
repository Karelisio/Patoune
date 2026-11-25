package com.karelisio.patoune

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.MedicationChange
import com.karelisio.patoune.data.PatouneDb
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TreatmentHistoryTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PatouneDb::class.java)

    @Test fun migration2To3KeepsMedicationsWithDefaults() {
        helper.createDatabase("migr3.db", 2).use { db ->
            db.execSQL("INSERT INTO medications (id, animalId, name, type, dose, unitsPerDose, times, startDate, notes, asNeeded, createdBy, updatedAt, deleted) " +
                "VALUES ('m1', 'a1', 'Flixotide', 'INHALER', '1 bouffée', 1.0, '08:00,20:00', '2026-03-01', '', 0, '', 1, 0)")
        }
        helper.runMigrationsAndValidate("migr3.db", 3, true).use { db ->
            db.query("SELECT name, alarmStyle, lateMinutes FROM medications WHERE id = 'm1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Flixotide", c.getString(0))
                assertEquals(0, c.getInt(1))
                assertEquals(30, c.getInt(2))
            }
            db.query("SELECT COUNT(*) FROM medication_changes").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test fun changesAreSyncedAndQueriedByPeriod() = runTest {
        val a = TestDevice("A", author = "Léa")
        val b = TestDevice("B")
        val med = a.store.save(Medication(animalId = "cat", name = "Flixotide", alarmStyle = true, lateMinutes = 15))
        a.store.save(MedicationChange(medicationId = med.id, animalId = "cat", at = 1_000, summary = "Dose : 1 bouffée → 2 bouffées"))
        a.store.save(MedicationChange(medicationId = med.id, animalId = "cat", at = 5_000, summary = "Fin : — → 3 mars 2026"))

        b.store.applyRemote(a.store.snapshotEvents())

        val got = b.db.medicationChanges().between("cat", 0, 2_000)
        assertEquals(listOf("Dose : 1 bouffée → 2 bouffées"), got.map { it.summary })
        assertEquals("Léa", got[0].createdBy)
        val medB = b.db.medications().byId(med.id)!!
        assertTrue(medB.alarmStyle)
        assertEquals(15, medB.lateMinutes)
    }
}
