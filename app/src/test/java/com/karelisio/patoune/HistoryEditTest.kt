package com.karelisio.patoune

import com.karelisio.patoune.data.BreathContext
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.BreathInput
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.EntryOps
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.ObsCategory
import com.karelisio.patoune.data.Observation
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.reminders.DoseLedger
import com.karelisio.patoune.reminders.DoseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalTime
import java.time.ZonedDateTime

/** Historique modifiable : saisie après coup et correction des prises, mesures et observations. */
@RunWith(RobolectricTestRunner::class)
class HistoryEditTest {
    private val yesterday = Dates.today().minusDays(1)
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(yesterday, LocalTime.of(h, m), Dates.zone()).toInstant().toEpochMilli()

    @Test fun pastDoseDeductsStockAndSkippingRestoresIt() = runTest {
        val d = TestDevice("A", author = "Léa")
        val ledger = DoseLedger(d.store, d.db)
        val med = d.store.save(Medication(animalId = "cat", name = "Flixotide", unitsPerDose = 2.0, stockRemaining = 100.0))

        val r = ledger.record(med, at(8, 10), DoseStatus.GIVEN, adjustStock = true)
        assertTrue(r is DoseResult.Saved)
        val log = (r as DoseResult.Saved).log
        assertEquals(at(8, 10), log.takenAt)
        assertEquals(98.0, d.db.medications().byId(med.id)!!.stockRemaining!!, 0.0)

        // Donné → sauté : le stock est restitué ; l'auteur reste celui de la saisie d'origine.
        d.author = "Tom"
        val edited = ledger.edit(log, at(8, 30), DoseStatus.SKIPPED)
        assertTrue(edited is DoseResult.Saved)
        val row = d.db.doses().byId(log.id)!!
        assertEquals(DoseStatus.SKIPPED, row.status)
        assertEquals(at(8, 30), row.takenAt)
        assertEquals("Léa", row.createdBy)
        assertEquals(100.0, d.db.medications().byId(med.id)!!.stockRemaining!!, 0.0)

        // Sauté → donné : déduit à nouveau.
        ledger.edit(row, row.takenAt, DoseStatus.GIVEN)
        assertEquals(98.0, d.db.medications().byId(med.id)!!.stockRemaining!!, 0.0)
    }

    @Test fun pastDoseWithoutStockAdjustmentLeavesStock() = runTest {
        val d = TestDevice("A")
        val med = d.store.save(Medication(animalId = "cat", name = "Flixotide", stockRemaining = 10.0))
        DoseLedger(d.store, d.db).record(med, at(8), DoseStatus.GIVEN, adjustStock = false)
        assertEquals(10.0, d.db.medications().byId(med.id)!!.stockRemaining!!, 0.0)
    }

    @Test fun guardAppliesToTheWindowOfThePastDate() = runTest {
        val d = TestDevice("A", author = "Léa")
        val ledger = DoseLedger(d.store, d.db)
        val med = d.store.save(Medication(animalId = "cat", name = "Flixotide", times = "08:00,20:00", stockRemaining = 10.0))
        ledger.record(med, at(8, 5), DoseStatus.GIVEN, adjustStock = true)

        // Plus tard dans la même fenêtre (celle de 8 h) : déjà donné.
        val later = ledger.record(med, at(9, 30), DoseStatus.GIVEN, adjustStock = true)
        assertTrue(later is DoseResult.AlreadyGiven)
        assertEquals(at(8, 5), (later as DoseResult.AlreadyGiven).at)
        assertEquals("Léa", later.by)
        // Un peu avant la prise existante, toujours pour l'horaire de 8 h : déjà donné aussi.
        assertTrue(ledger.record(med, at(7, 45), DoseStatus.GIVEN, adjustStock = true) is DoseResult.AlreadyGiven)
        // Rien n'a été enregistré ni déduit.
        assertEquals(1, d.db.doses().since(med.id, 0).size)
        assertEquals(9.0, d.db.medications().byId(med.id)!!.stockRemaining!!, 0.0)

        // Horaire de 20 h : autre fenêtre ; une prise sautée ne déclenche jamais le garde-fou.
        assertTrue(ledger.record(med, at(20, 10), DoseStatus.GIVEN, adjustStock = true) is DoseResult.Saved)
        assertTrue(ledger.record(med, at(9), DoseStatus.SKIPPED, adjustStock = true) is DoseResult.Saved)
        // Confirmation explicite : la seconde prise est enregistrée.
        assertTrue(ledger.record(med, at(9, 30), DoseStatus.GIVEN, adjustStock = true, force = true) is DoseResult.Saved)
        assertEquals(7.0, d.db.medications().byId(med.id)!!.stockRemaining!!, 0.0)

        // Corriger l'heure d'une prise dans sa propre fenêtre ne la compte pas comme un doublon d'elle-même.
        val evening = d.db.doses().since(med.id, 0).first { it.takenAt == at(20, 10) }
        assertTrue(ledger.edit(evening, at(20, 40), DoseStatus.GIVEN) is DoseResult.Saved)
        // … mais la déplacer dans la fenêtre du matin, si.
        assertTrue(ledger.edit(evening, at(10), DoseStatus.GIVEN) is DoseResult.AlreadyGiven)
    }

    @Test fun breathEditRecomputesRateFromTapsAndDuration() = runTest {
        val d = TestDevice("A")
        val auto = d.store.save(BreathCount(animalId = "cat", at = at(22), taps = 12, durationSec = 30, bpm = 24.0, estimated = true))

        val saved = EntryOps.saveBreath(d.store, auto, at(21, 15), BreathInput.Taps(9, 15), BreathContext.REST, "  après le jeu ")!!
        val row = d.db.breaths().byId(auto.id)!!
        assertEquals(saved, row)
        assertEquals(36.0, row.bpm, 0.0)
        assertEquals(9, row.taps)
        assertEquals(15, row.durationSec)
        assertEquals(at(21, 15), row.at)
        assertEquals(BreathContext.REST, row.context)
        assertEquals("après le jeu", row.note)
        // Valeur corrigée : ce n'est plus une estimation du comptage auto.
        assertFalse(row.estimated)

        // Saisie directe en resp/min.
        val direct = EntryOps.saveBreath(d.store, row, row.at, BreathInput.Rate(28.0), row.context, row.note)!!
        assertEquals(28.0, direct.bpm, 0.0)
        assertEquals(60, direct.durationSec)
        // Saisie invalide : rien n'est écrit.
        assertNull(EntryOps.saveBreath(d.store, direct, direct.at, BreathInput.Taps(0, 30), direct.context, ""))
        assertEquals(28.0, d.db.breaths().byId(auto.id)!!.bpm, 0.0)
    }

    @Test fun observationEdit() = runTest {
        val d = TestDevice("A", author = "Léa")
        val o = d.store.save(Observation(animalId = "cat", at = at(9), category = ObsCategory.APPETITE, value = "Diminué"))

        val saved = EntryOps.saveObservation(d.store, o, at(7, 30), ObsCategory.VOMIT, "Boule de poils", "dans le salon")!!
        val row = d.db.observations().byId(o.id)!!
        assertEquals(saved, row)
        assertEquals(ObsCategory.VOMIT, row.category)
        assertEquals("Boule de poils", row.value)
        assertEquals(at(7, 30), row.at)
        assertEquals("dans le salon", row.note)
        assertEquals("Léa", row.createdBy)

        // Valeur hors des options de la catégorie (clés françaises) : refusée.
        assertNull(EntryOps.saveObservation(d.store, row, row.at, ObsCategory.WATER, "Boule de poils", ""))
        assertEquals(ObsCategory.VOMIT, d.db.observations().byId(o.id)!!.category)

        // Ajout après coup : nouvelle ligne datée du passé.
        val past = EntryOps.saveObservation(d.store, Observation(animalId = "cat"), at(6), ObsCategory.WATER, "Peu", "")!!
        assertTrue(past.id.isNotEmpty())
        assertEquals(at(6), d.db.observations().byId(past.id)!!.at)
    }
}
