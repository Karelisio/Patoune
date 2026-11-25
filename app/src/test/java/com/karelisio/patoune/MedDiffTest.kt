package com.karelisio.patoune

import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.MedDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedDiffTest {
    private val base = Medication(id = "m1", name = "Flixotide", type = MedType.INHALER, dose = "1 bouffée", times = "08:00,20:00", startDate = "2026-03-01")

    @Test fun creationIsSummarized() {
        val lines = MedDiff.diff(null, base)
        assertEquals(1, lines.size)
        assertEquals("Nouveau traitement : 1 bouffée, 08:00, 20:00, dès le 1 mars 2026", lines[0])
        assertTrue(MedDiff.diff(null, base.copy(asNeeded = true, dose = ""))[0].startsWith("Nouveau traitement : si besoin"))
    }

    @Test fun doseTimesAndEndDate() {
        val new = base.copy(dose = "2 bouffées", times = "08:00", endDate = "2026-03-03")
        assertEquals(
            listOf("Dose : 1 bouffée → 2 bouffées", "Horaires : 08:00, 20:00 → 08:00", "Fin : — → 3 mars 2026"),
            MedDiff.diff(base, new),
        )
    }

    @Test fun unitsNameStartAndAlarm() {
        val new = base.copy(name = "Flixotide 125", unitsPerDose = 2.0, startDate = "2026-03-02", alarmStyle = true)
        assertEquals(
            listOf("Nom : Flixotide → Flixotide 125", "Bouffée(s) par prise : 1 → 2", "Début : 1 mars 2026 → 2 mars 2026", "Rappel type réveil : activé"),
            MedDiff.diff(base, new),
        )
        assertEquals(listOf("Bouffée(s) par prise : 1 → 0,5"), MedDiff.diff(base, base.copy(unitsPerDose = 0.5)))
    }

    @Test fun asNeededToggle() {
        assertEquals(listOf("Mode : horaires fixes → si besoin"), MedDiff.diff(base, base.copy(asNeeded = true)))
        val asNeeded = base.copy(asNeeded = true)
        assertEquals(listOf("Mode : si besoin → horaires 09:00"), MedDiff.diff(asNeeded, base.copy(times = "09:00")))
        // En « si besoin », les horaires masqués ne comptent pas.
        assertTrue(MedDiff.diff(asNeeded, asNeeded.copy(times = "10:00")).isEmpty())
    }

    @Test fun minorChangesProduceNothing() {
        val new = base.copy(notes = "Avec la chambre", stockRemaining = 120.0, stockAlertAt = 20.0, renewalDate = "2026-05-01",
            updatedAt = 99, lateMinutes = 10, times = "20:00, 08:00", dose = " 1 bouffée ")
        assertTrue(MedDiff.diff(base, new).isEmpty())
    }
}
