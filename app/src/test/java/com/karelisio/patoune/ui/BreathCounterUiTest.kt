package com.karelisio.patoune.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karelisio.patoune.R
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.ui.Ui.c
import com.karelisio.patoune.ui.Ui.plural
import com.karelisio.patoune.ui.Ui.str
import com.karelisio.patoune.ui.screens.BreathCounterScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = UiTestApp::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class BreathCounterUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun twelveTapsIn30SecondsGives24() {
        runBlocking {
            c.store.save(Animal(name = "Mochi"))
            c.settings.setBreathDuration(30)
        }
        // Horloge contrôlée : un tap toutes les 2,5 s, puis fin du chrono à 30 s.
        var clock = 1_000_000L
        rule.setScreen { BreathCounterScreen(it, nightStart = false, clock = { clock }) }
        rule.waitForIdle()
        // La boucle d'images du chrono tourne en continu : on avance l'horloge des images à la main.
        rule.mainClock.autoAdvance = false
        val zone = rule.onNodeWithContentDescription(str(R.string.a11y_breath_tap))
        repeat(12) {
            zone.performClick()
            rule.mainClock.advanceTimeByFrame()
            clock += 2_500
        }
        clock = 1_000_000L + 30_000
        repeat(5) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.a11y_breath_result, "24")).assertIsDisplayed()
        rule.onNodeWithText(plural(R.plurals.breath_result_detail, 12, 12, 30)).assertIsDisplayed()
    }
}
