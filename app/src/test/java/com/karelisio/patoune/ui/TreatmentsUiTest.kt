package com.karelisio.patoune.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.karelisio.patoune.R
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.DoseStatus
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.ui.Ui.c
import com.karelisio.patoune.ui.Ui.str
import com.karelisio.patoune.ui.screens.DoubleDoseDialog
import com.karelisio.patoune.ui.screens.GiveController
import com.karelisio.patoune.ui.screens.MedCard
import com.karelisio.patoune.ui.screens.MedStatus
import com.karelisio.patoune.ui.screens.TreatmentsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = UiTestApp::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TreatmentsUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var med: Medication

    @Before fun seed() = runBlocking {
        val cat = c.store.save(Animal(name = "Mochi"))
        med = c.store.save(Medication(animalId = cat.id, name = "Flixotide", dose = "2 bouffées"))
    }

    private fun givenCount() = runBlocking { c.db.doses().allRows().count { !it.deleted && it.status == DoseStatus.GIVEN } }

    @Test fun giveFromTreatmentsScreenMarksDose() {
        rule.setScreen { TreatmentsScreen(it) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Flixotide")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(str(R.string.a11y_mark_given)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription(str(R.string.a11y_already_given))).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, givenCount())
    }

    /**
     * Garde-fou double dose : deux « Donné » pour la même prise (double tap avant le rafraîchissement,
     * ou prise faite entre-temps sur un autre téléphone) → le second ouvre « Déjà donné ? » et n'enregistre rien.
     * La carte et le dialogue sont ceux de l'écran Traitements ; l'état affiché reste figé comme pendant la course.
     */
    @Test fun secondGiveShowsDoubleDoseDialog() {
        rule.setContent { FrozenTreatment(med) }
        val give = rule.onNodeWithContentDescription(str(R.string.a11y_mark_given))
        give.performClick()
        rule.waitUntil(5_000) { givenCount() == 1 }
        give.performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText(str(R.string.med_double_title))).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.med_double_title)).assertIsDisplayed()
        assertEquals(1, givenCount())
        // « Non » : rien de plus n'est enregistré.
        rule.onNodeWithText(str(R.string.action_no)).performClick()
        rule.onNodeWithText(str(R.string.med_double_title)).assertDoesNotExist()
        assertEquals(1, givenCount())
    }

    @Composable
    private fun FrozenTreatment(med: Medication) {
        val scope = rememberCoroutineScope()
        val ctrl = remember { GiveController(c, scope) }
        com.karelisio.patoune.ui.theme.PatouneTheme {
            MedCard(MedStatus(med, nextAt = null, given = null), shared = false, onGive = { ctrl.give(med) }, onOpen = {})
            DoubleDoseDialog(ctrl, shared = false)
        }
    }
}
