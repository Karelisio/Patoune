package com.karelisio.patoune

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.CareItem
import com.karelisio.patoune.data.CareKind
import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.domain.CareRenewal
import com.karelisio.patoune.domain.Frequency
import com.karelisio.patoune.domain.MedDiff
import com.karelisio.patoune.domain.MedDiff.Change
import com.karelisio.patoune.ui.FreqText
import com.karelisio.patoune.ui.MedDiffText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

/** Fréquences de traitement et soins récurrents : migration v4, historique, résumés traduits, « Fait aujourd'hui ». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class FrequencyTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PatouneDb::class.java)
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val base = Medication(id = "m1", name = "Flixotide", type = MedType.INHALER, dose = "1 bouffée", times = "08:00,20:00", startDate = "2026-03-01")

    @Test fun migration3To4KeepsDailyAndNoRenewal() {
        helper.createDatabase("freq.db", 3).use { db ->
            db.execSQL("INSERT INTO medications (id, animalId, name, type, dose, unitsPerDose, times, startDate, notes, asNeeded, alarmStyle, lateMinutes, createdBy, updatedAt, deleted) " +
                "VALUES ('m1', 'a1', 'Flixotide', 'INHALER', '1 bouffée', 1.0, '08:00,20:00', '2026-03-01', '', 0, 0, 30, '', 1, 0)")
            db.execSQL("INSERT INTO care_items (id, animalId, kind, name, date, nextDue, notes, createdBy, updatedAt, deleted) " +
                "VALUES ('c1', 'a1', 'VACCINE', 'Typhus', '2026-01-10', '2027-01-10', '', '', 1, 0)")
        }
        helper.runMigrationsAndValidate("freq.db", 4, true).use { db ->
            db.query("SELECT freqType, freqInterval, weekdays FROM medications WHERE id = 'm1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("DAILY", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals("", c.getString(2))
            }
            db.query("SELECT repeatMonths, nextDue FROM care_items WHERE id = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
                assertEquals("2027-01-10", c.getString(1))
            }
        }
    }

    @Test fun frequencyChangeInHistory() {
        val quarterly = base.copy(freqType = FreqType.EVERY_N_MONTHS, freqInterval = 3)
        val anchor = LocalDate.of(2026, 3, 1)
        assertEquals(
            listOf(Change.Recurrence(Frequency(anchor = anchor), Frequency(FreqType.EVERY_N_MONTHS, 3, anchor = anchor))),
            MedDiff.diff(base, quarterly),
        )
        assertEquals(listOf("Fréquence : Tous les jours → Tous les 3 mois, le 1"), MedDiffText.lines(ctx, base.type, MedDiff.diff(base, quarterly)))
        assertEquals(
            listOf("Nouveau traitement : 1 bouffée, Tous les 3 mois, le 1, à 08:00, 20:00, dès le 1 mars 2026"),
            MedDiffText.lines(ctx, base.type, MedDiff.diff(null, quarterly)),
        )
        // Valeurs sans effet (intervalle d'un traitement quotidien, « certains jours » sans jour coché) : aucun changement.
        assertTrue(MedDiff.diff(base, base.copy(freqInterval = 4)).isEmpty())
        assertTrue(MedDiff.diff(base, base.copy(freqType = FreqType.WEEKDAYS)).isEmpty())
        assertTrue(MedDiff.diff(base.copy(asNeeded = true), base.copy(asNeeded = true, freqType = FreqType.EVERY_N_DAYS, freqInterval = 2)).isEmpty())
        val mwf = base.copy(freqType = FreqType.WEEKDAYS, weekdays = "1,3,5")
        assertEquals(
            Change.Recurrence(Frequency(anchor = anchor), Frequency(FreqType.WEEKDAYS, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), anchor)),
            MedDiff.diff(base, mwf).single(),
        )
    }

    @Test fun summariesInFrench() {
        assertEquals("Tous les jours à 08:00, 20:00", FreqText.summary(ctx, base))
        assertEquals("Lun, mer, ven à 08:00", FreqText.summary(ctx, base.copy(times = "08:00", freqType = FreqType.WEEKDAYS, weekdays = "5,1,3")))
        assertEquals("Tous les 3 mois, le 12, à 09:00",
            FreqText.summary(ctx, base.copy(times = "09:00", freqType = FreqType.EVERY_N_MONTHS, freqInterval = 3, startDate = "2026-01-12")))
        assertEquals("Tous les mois, le 31, à 09:00",
            FreqText.summary(ctx, base.copy(times = "09:00", freqType = FreqType.EVERY_N_MONTHS, startDate = "2026-01-31")))
        assertEquals("Tous les 2 jours à 08:00", FreqText.summary(ctx, base.copy(times = "08:00", freqType = FreqType.EVERY_N_DAYS, freqInterval = 2)))
        // Le 3 mars 2026 est un mardi.
        assertEquals("Toutes les 2 semaines, le mardi, à 09:00",
            FreqText.summary(ctx, base.copy(times = "09:00", freqType = FreqType.EVERY_N_WEEKS, freqInterval = 2, startDate = "2026-03-03")))
        assertEquals("Toutes les semaines, lun, jeu, à 09:00",
            FreqText.summary(ctx, base.copy(times = "09:00", freqType = FreqType.EVERY_N_WEEKS, weekdays = "1,4")))
    }

    @Test @Config(qualifiers = "en") fun summariesInEnglish() {
        assertEquals("Every day at 08:00, 20:00", FreqText.summary(ctx, base))
        assertEquals("Mon, Wed, Fri at 08:00", FreqText.summary(ctx, base.copy(times = "08:00", freqType = FreqType.WEEKDAYS, weekdays = "1,3,5")))
        assertEquals("Every 3 months, on day 12, at 09:00",
            FreqText.summary(ctx, base.copy(times = "09:00", freqType = FreqType.EVERY_N_MONTHS, freqInterval = 3, startDate = "2026-01-12")))
        assertEquals("Frequency: Every day → Every 2 weeks, on Tuesday",
            MedDiffText.lines(ctx, base.type, MedDiff.diff(base, base.copy(freqType = FreqType.EVERY_N_WEEKS, freqInterval = 2, startDate = "2026-03-03")))
                .first { it.startsWith("Frequency") })
    }

    @Test fun careRenewalComputesDueDate() {
        assertEquals("2026-04-10", CareRenewal.nextDue("2026-01-10", 3))
        assertEquals("2026-02-28", CareRenewal.nextDue("2026-01-31", 1))
        assertNull(CareRenewal.nextDue("2026-01-10", null))
        val manual = CareItem(date = "2026-01-10", nextDue = "2026-05-01")
        assertEquals("2026-05-01", CareRenewal.withComputedDue(manual).nextDue)
        assertEquals("2027-01-10", CareRenewal.withComputedDue(manual.copy(repeatMonths = 12)).nextDue)
    }

    @Test fun doneTodayAddsNewEntryAndKeepsHistory() = runTest {
        val d = TestDevice("A")
        val cat = d.store.save(Animal(name = "Mochi"))
        val old = d.store.save(CareItem(animalId = cat.id, kind = CareKind.DEWORM, name = "Milbemax", date = "2026-08-20", nextDue = "2026-11-20", repeatMonths = 3, notes = "½ cp"))
        val today = LocalDate.of(2026, 11, 26)
        val renewed = d.store.save(CareRenewal.doneToday(old, today))
        assertTrue(renewed.id.isNotEmpty() && renewed.id != old.id)
        assertEquals("2026-11-26", renewed.date)
        assertEquals("2027-02-26", renewed.nextDue)
        assertEquals(3, renewed.repeatMonths)
        assertEquals(CareKind.DEWORM to "Milbemax", renewed.kind to renewed.name)
        val rows = d.db.care().allRows()
        // L'ancienne entrée reste dans l'historique, intacte…
        assertEquals(2, rows.size)
        assertEquals("2026-11-20", rows.first { it.id == old.id }.nextDue)
        // … mais seule la plus récente compte pour les rappels (pas de doublon).
        assertEquals(listOf(renewed.id), CareRenewal.latestPerProduct(rows).map { it.id })
        // Un autre produit garde sa propre échéance.
        val vaccine = d.store.save(CareItem(animalId = cat.id, kind = CareKind.VACCINE, name = "Typhus", date = "2026-01-10", nextDue = "2027-01-10"))
        assertEquals(setOf(renewed.id, vaccine.id), CareRenewal.latestIds(d.db.care().allRows()))
    }
}
