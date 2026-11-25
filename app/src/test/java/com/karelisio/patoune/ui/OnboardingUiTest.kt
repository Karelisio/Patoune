package com.karelisio.patoune.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karelisio.patoune.R
import com.karelisio.patoune.ui.Ui.str
import com.karelisio.patoune.ui.components.AppPermission
import com.karelisio.patoune.ui.screens.OnboardingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = UiTestApp::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OnboardingUiTest {
    @get:Rule val rule = createComposeRule()

    /** Passe la présentation, choisit un mode puis « Continuer ». */
    private fun chooseMode(title: String) {
        rule.setScreen { OnboardingScreen(it) }
        rule.onNodeWithText(str(R.string.onboarding_start)).performClick()
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithText(str(R.string.action_continue)).performClick()
        rule.waitForIdle()
    }

    @Test fun joinSkipsAnimalForm() {
        chooseMode(str(R.string.household_join_title))
        // Page suivante : la première autorisation, pas la fiche animal.
        rule.onNodeWithText(str(AppPermission.entries.first().titleRes)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.onboarding_animal)).assertDoesNotExist()
    }

    @Test fun soloShowsAnimalForm() {
        chooseMode(str(R.string.settings_solo))
        rule.onNodeWithText(str(R.string.onboarding_animal)).assertIsDisplayed()
        rule.onNodeWithText(str(AppPermission.entries.first().titleRes)).assertDoesNotExist()
    }
}
