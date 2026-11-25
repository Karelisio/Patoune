package com.karelisio.patoune.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.karelisio.patoune.R
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.ui.Ui.c
import com.karelisio.patoune.ui.Ui.str
import com.karelisio.patoune.ui.screens.AnimalsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = UiTestApp::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AnimalsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deleteAnimalThroughDialogRemovesIt() {
        runBlocking {
            c.store.save(Animal(name = "Mochi", sortOrder = 0))
            c.store.save(Animal(name = "Rex", species = "Chien", sortOrder = 1))
        }
        rule.setScreen { AnimalsScreen(it) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Mochi")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(str(R.string.delete_named, "Mochi")).performClick()
        rule.onNodeWithText(str(R.string.delete_named_q, "Mochi")).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.action_delete)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Mochi")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Rex").assertIsDisplayed()
        assertEquals(listOf("Rex"), runBlocking { c.db.animals().allRows().filter { !it.deleted }.map { it.name } })
    }
}
