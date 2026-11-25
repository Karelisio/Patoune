package com.karelisio.patoune

import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.MedDiff
import com.karelisio.patoune.domain.MedDiff.Change
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Changements structurés (logique pure) ; la mise en forme traduite est testée dans TextsTest. */
class MedDiffTest {
    private val base = Medication(id = "m1", name = "Flixotide", type = MedType.INHALER, dose = "1 bouffée", times = "08:00,20:00", startDate = "2026-03-01")
    private val eight = LocalTime.of(8, 0)
    private val twenty = LocalTime.of(20, 0)

    @Test fun creationIsSummarized() {
        val lines = MedDiff.diff(null, base)
        assertEquals(listOf(Change.Created("1 bouffée", false, listOf(eight, twenty), LocalDate.of(2026, 3, 1), null)), lines)
        val asNeeded = MedDiff.diff(null, base.copy(asNeeded = true, dose = "")).single() as Change.Created
        assertTrue(asNeeded.asNeeded)
        assertEquals("", asNeeded.dose)
    }

    @Test fun doseTimesAndEndDate() {
        val new = base.copy(dose = "2 bouffées", times = "08:00", endDate = "2026-03-03")
        assertEquals(
            listOf(Change.Dose("1 bouffée", "2 bouffées"), Change.Times(listOf(eight, twenty), listOf(eight)), Change.End(null, LocalDate.of(2026, 3, 3))),
            MedDiff.diff(base, new),
        )
    }

    @Test fun unitsNameStartAndAlarm() {
        val new = base.copy(name = "Flixotide 125", unitsPerDose = 2.0, startDate = "2026-03-02", alarmStyle = true)
        assertEquals(
            listOf(Change.Name("Flixotide", "Flixotide 125"), Change.Units(1.0, 2.0), Change.Start(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2)), Change.Alarm(true)),
            MedDiff.diff(base, new),
        )
        assertEquals(listOf(Change.Units(1.0, 0.5)), MedDiff.diff(base, base.copy(unitsPerDose = 0.5)))
    }

    @Test fun asNeededToggle() {
        assertEquals(listOf(Change.ToAsNeeded), MedDiff.diff(base, base.copy(asNeeded = true)))
        val asNeeded = base.copy(asNeeded = true)
        assertEquals(listOf(Change.ToFixed(listOf(LocalTime.of(9, 0)))), MedDiff.diff(asNeeded, base.copy(times = "09:00")))
        // En « si besoin », les horaires masqués ne comptent pas.
        assertTrue(MedDiff.diff(asNeeded, asNeeded.copy(times = "10:00")).isEmpty())
    }

    @Test fun minorChangesProduceNothing() {
        val new = base.copy(notes = "Avec la chambre", stockRemaining = 120.0, stockAlertAt = 20.0, renewalDate = "2026-05-01",
            updatedAt = 99, lateMinutes = 10, times = "20:00, 08:00", dose = " 1 bouffée ")
        assertTrue(MedDiff.diff(base, new).isEmpty())
    }
}
