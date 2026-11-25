package com.karelisio.patoune

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.karelisio.patoune.data.CrisisTypes
import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.ObsCategory
import com.karelisio.patoune.domain.Insights
import com.karelisio.patoune.domain.MedDiff
import com.karelisio.patoune.ui.InsightsText
import com.karelisio.patoune.ui.Labels
import com.karelisio.patoune.ui.MedDiffText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Phrases traduites (ressources) : français par défaut, anglais via values-en, clés stockées stables. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TextsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Paris")
    private val base = Medication(id = "m1", name = "Flixotide", type = MedType.INHALER, dose = "1 bouffée", times = "08:00,20:00", startDate = "2026-03-01")

    private fun crisis(d: LocalDate, hour: Int) = Crisis(id = "$d-$hour", startAt = LocalDateTime.of(d, java.time.LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli())

    @Test fun insightsSentencesInFrench() {
        assertEquals(
            "Les jours de crise, le pollen moyen était de 90 grains/m³ contre 30 les autres jours.",
            InsightsText.pollen(ctx, Insights.Comparison(90.0, 30.0, 3, 4)),
        )
        assertTrue(InsightsText.pollen(ctx, null).contains("pas encore assez"))
        val d = LocalDate.of(2026, 11, 1)
        val crises = listOf(crisis(d, 7), crisis(d, 19), crisis(d, 21), crisis(d, 23), crisis(d, 3), crisis(d, 14))
        val s = Insights.summarize(crises, emptyList(), emptyList(), zone, d, d)
        assertEquals("Les crises surviennent le plus souvent le soir (3 sur 6).", InsightsText.dayParts(ctx, s))
        assertEquals("Pollens 3 (75 %)", InsightsText.shares(ctx, listOf(Insights.Share("Pollens", 3, 75))))
    }

    @Test fun medHistoryLinesInFrench() {
        assertEquals(listOf("Nouveau traitement : 1 bouffée, 08:00, 20:00, dès le 1 mars 2026"), MedDiffText.lines(ctx, base.type, MedDiff.diff(null, base)))
        val new = base.copy(dose = "2 bouffées", times = "08:00", endDate = "2026-03-03", unitsPerDose = 0.5, alarmStyle = true)
        assertEquals(
            listOf("Dose : 1 bouffée → 2 bouffées", "Bouffées par prise : 1 → 0,5", "Horaires : 08:00, 20:00 → 08:00", "Fin : — → 3 mars 2026", "Rappel type réveil : activé"),
            MedDiffText.lines(ctx, new.type, MedDiff.diff(base, new)),
        )
        assertEquals(listOf("Mode : horaires fixes → si besoin"), MedDiffText.lines(ctx, base.type, MedDiff.diff(base, base.copy(asNeeded = true))))
    }

    @Test fun storedKeysStayFrenchAndDisplayFallsBack() {
        // Les valeurs stockées ne changent pas ; une valeur inconnue (saisie libre, ancienne donnée) s'affiche telle quelle.
        assertEquals(listOf("Normal", "Diminué", "Absent", "Augmenté"), ObsCategory.APPETITE.options)
        assertTrue("Toux" in CrisisTypes.all && "Pollens" in CrisisTypes.triggers)
        assertTrue(ObsCategory.entries.all { c -> c.options.all { it in ObsCategory.optionLabels } })
        assertTrue((CrisisTypes.all + CrisisTypes.triggers).all { it in CrisisTypes.labels })
        assertEquals("Boule de poils", Labels.obs(ctx, "Boule de poils"))
        assertEquals("Herbe à chat", Labels.crisis(ctx, "Herbe à chat"))
        assertEquals("Toux, Sifflement", Labels.csv(ctx, CrisisTypes.labels, "Toux,Sifflement"))
    }

    @Test @Config(qualifiers = "en") fun englishTranslation() {
        assertEquals("Hairball", Labels.obs(ctx, "Boule de poils"))
        assertEquals("Cough, Wheezing", Labels.csv(ctx, CrisisTypes.labels, "Toux,Sifflement"))
        assertEquals("Catnip", Labels.crisis(ctx, "Catnip"))
        assertEquals("Mode: fixed times → as needed", MedDiffText.lines(ctx, base.type, MedDiff.diff(base, base.copy(asNeeded = true))).single())
        assertEquals("3 puffs", ctx.resources.getQuantityString(MedType.INHALER.unitRes, 3, "3"))
        assertEquals("en", RuntimeEnvironment.getQualifiers().substringBefore('-'))
    }
}
