package com.karelisio.patoune.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.karelisio.patoune.R
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.ui.Ui.c
import com.karelisio.patoune.ui.Ui.str
import com.karelisio.patoune.ui.screens.CrisisEditScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.Config

/** « Noter une crise » : rien n'est enregistré tant qu'on n'a pas touché Enregistrer (ou filmé). */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiTestApp::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CrisisEditUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun animal() { runBlocking { c.store.save(Animal(name = "Mochi", sortOrder = 0)) } }

    private fun crises() = runBlocking { c.db.crises().allRows().filter { !it.deleted } }

    private lateinit var vm: MainViewModel

    private fun open() {
        rule.setScreen { vm = it; CrisisEditScreen(it, id = null, startRecording = false) }
        // L'animal sélectionné est chargé de façon asynchrone (lent au premier test : base et DataStore à froid).
        rule.waitUntil(20_000) { vm.selected.value != null }
        rule.onNodeWithText(str(R.string.crisis_draft_note)).assertIsDisplayed()
    }

    @Test fun leavingWithArrowSavesNothing() {
        open()
        rule.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        rule.waitForIdle()
        assertTrue(crises().isEmpty())
    }

    @Test fun systemBackSavesNothing() {
        open()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue(crises().isEmpty())
    }

    @Test fun saveButtonRecordsOneCrisis() {
        open()
        rule.onNodeWithText(str(R.string.action_save)).performScrollTo().performClick()
        rule.waitUntil(10_000) { crises().isNotEmpty() }
        assertEquals(1, crises().size)
    }
}
