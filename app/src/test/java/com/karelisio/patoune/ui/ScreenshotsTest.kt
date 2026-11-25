package com.karelisio.patoune.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.BreathContext
import com.karelisio.patoune.data.BreathCount
import com.karelisio.patoune.data.CareItem
import com.karelisio.patoune.data.CareKind
import com.karelisio.patoune.data.Crisis
import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.MedType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.data.ObsCategory
import com.karelisio.patoune.data.Observation
import com.karelisio.patoune.data.VetVisit
import com.karelisio.patoune.data.WeightEntry
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.ui.Ui.c
import com.karelisio.patoune.ui.screens.BreathCounterScreen
import com.karelisio.patoune.ui.screens.BreathScreen
import com.karelisio.patoune.ui.screens.EmergencyScreen
import com.karelisio.patoune.ui.screens.HealthScreen
import com.karelisio.patoune.ui.screens.HomeScreen
import com.karelisio.patoune.ui.screens.JournalScreen
import com.karelisio.patoune.ui.screens.TreatmentsScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone

/**
 * Captures Roborazzi des écrans principaux, en clair et en sombre, avec des données factices fixes
 * (posées par rapport à aujourd'hui, fuseau Europe/Paris, appli en français).
 * Enregistrées seulement avec `./gradlew recordRoborazziDebug` (dans app/build/outputs/roborazzi) ;
 * sous `testDebugUnitTest`, les écrans sont rendus mais rien n'est écrit ni comparé.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = UiTestApp::class, qualifiers = "fr-rFR-" + RobolectricDeviceQualifiers.Pixel7)
class ScreenshotsTest(private val variant: String, private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants() = listOf(arrayOf<Any>("clair", false), arrayOf<Any>("sombre", true))
    }

    @get:Rule val rule = createComposeRule()
    private val zone = TimeZone.getDefault()

    @Before fun seed() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        runBlocking { seedData() }
    }

    @After fun restore() = TimeZone.setDefault(zone)

    @Test fun accueil() = capture("accueil", "Flixotide") { HomeScreen(it) }
    @Test fun traitements() = capture("traitements", "Prednisolone") { TreatmentsScreen(it) }
    @Test fun souffle() = capture("souffle", "Mochi") { BreathScreen(it) }
    @Test fun journal() = capture("journal", "Respiration abdominale") { JournalScreen(it) }
    @Test fun carnet() = capture("carnet", "Typhus-coryza") { HealthScreen(it) }
    @Test fun urgence() = capture("urgence", "Pénicilline") { EmergencyScreen(it) }
    @Test fun compteurNuit() = capture("compteur_nuit", "Mochi") { BreathCounterScreen(it, nightStart = true) }

    /** Affiche l'écran, attend que [marker] (donnée chargée depuis Room) soit visible, puis capture. */
    private fun capture(name: String, marker: String, screen: @Composable (MainViewModel) -> Unit) {
        rule.setScreen(dark = dark, dynamicColor = false, screen = screen)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Laisse arriver les autres flux Room (chargés sur un autre fil) et finir les apparitions.
        repeat(3) { Thread.sleep(150); rule.waitForIdle() }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${name}_$variant.png")
    }

    private fun at(daysAgo: Long, h: Int, m: Int): Long =
        Dates.today().minusDays(daysAgo).atTime(LocalTime.of(h, m)).atZone(Dates.zone()).toInstant().toEpochMilli()

    private fun day(offset: Long): String = Dates.today().plusDays(offset).toString()

    private suspend fun seedData() {
        val s = c.store
        val mochi = s.save(
            Animal(
                name = "Mochi", species = "Chat", breed = "Européen", sex = "Femelle", birthDate = LocalDate.of(2019, 4, 12).toString(),
                chipNumber = "250269812345678", sterilized = true, vetName = "Dr Martin", vetPhone = "01 23 45 67 89",
                emergencyVetName = "Clinique VetUrgence", emergencyVetPhone = "01 98 76 54 32", allergies = "Pénicilline",
                history = "Asthme félin diagnostiqué en 2023", breathThreshold = 30,
            ),
        )
        c.settings.selectAnimal(mochi.id)
        c.settings.setOnboardingDone()
        val flixo = s.save(
            Medication(
                animalId = mochi.id, name = "Flixotide 125 µg", type = MedType.INHALER, dose = "2 bouffées", unitsPerDose = 2.0,
                times = "08:00,20:00", startDate = day(-60), stockRemaining = 48.0, stockAlertAt = 20.0,
            ),
        )
        s.save(Medication(animalId = mochi.id, name = "Ventoline", type = MedType.INHALER, dose = "1 bouffée", asNeeded = true, startDate = day(-60)))
        s.save(Medication(animalId = mochi.id, name = "Prednisolone", type = MedType.TABLET, dose = "½ comprimé", times = "09:00", startDate = day(-5), endDate = day(10)))
        s.save(DoseLog(medicationId = flixo.id, animalId = mochi.id, takenAt = at(1, 20, 4)))

        val bpm = listOf(22, 24, 21, 26, 23, 25, 28, 24, 22, 27, 31, 25, 23, 24)
        bpm.forEachIndexed { i, v ->
            s.save(BreathCount(animalId = mochi.id, at = at((bpm.size - i).toLong(), 23, 15), taps = v / 2, durationSec = 30, bpm = v.toDouble(), context = BreathContext.SLEEP))
        }
        s.save(
            Crisis(
                animalId = mochi.id, startAt = at(3, 14, 20), durationSec = 95, intensity = 3, types = "Toux,Respiration abdominale",
                triggers = "Litière,Pollens", emergencyTreatment = "Ventoline 1 bouffée", notes = "Après le changement de litière.",
            ),
        )
        s.save(Crisis(animalId = mochi.id, startAt = at(11, 7, 10), durationSec = 40, intensity = 2, types = "Toux", triggers = "Ménage"))
        s.save(Observation(animalId = mochi.id, at = at(0, 8, 30), category = ObsCategory.APPETITE, value = "Normal"))
        s.save(Observation(animalId = mochi.id, at = at(1, 19, 0), category = ObsCategory.BEHAVIOR, value = "Calme"))
        s.save(CareItem(animalId = mochi.id, kind = CareKind.VACCINE, name = "Typhus-coryza", date = day(-200), nextDue = day(165)))
        s.save(CareItem(animalId = mochi.id, kind = CareKind.DEWORM, name = "Milbemax", date = day(-30), nextDue = day(60)))
        s.save(CareItem(animalId = mochi.id, kind = CareKind.FLEA, name = "Frontline", date = day(-10), nextDue = day(20)))
        listOf(-120L to 4.05, -60L to 4.2, -7L to 4.25).forEach { (d, kg) -> s.save(WeightEntry(animalId = mochi.id, date = day(d), kg = kg)) }
        s.save(VetVisit(animalId = mochi.id, date = day(-20), reason = "Contrôle asthme", report = "Auscultation correcte, traitement de fond maintenu."))
    }
}
