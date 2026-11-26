package com.karelisio.patoune.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.karelisio.patoune.R
import com.karelisio.patoune.data.Animal
import com.karelisio.patoune.data.DoseLog
import com.karelisio.patoune.data.FreqType
import com.karelisio.patoune.data.Medication
import com.karelisio.patoune.domain.Dates
import com.karelisio.patoune.reminders.DoseResult
import com.karelisio.patoune.ui.Ui.c
import com.karelisio.patoune.ui.Ui.str
import com.karelisio.patoune.ui.screens.HomeScreen
import com.karelisio.patoune.ui.screens.medStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Traitement trimestriel donné il y a 3 jours (cas signalé) : la prise couvre le trimestre, l'échéance
 * repart de cette prise, et l'accueil le range « À venir » au lieu de « À donner ».
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = UiTestApp::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SpacedDosesUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var quarterly: Medication
    private lateinit var daily: Medication
    private val threeDaysAgo = System.currentTimeMillis() - 3 * 86_400_000L

    @Before fun seed(): Unit = runBlocking {
        val cat = c.store.save(Animal(name = "Mochi"))
        quarterly = c.store.save(Medication(animalId = cat.id, name = "Milbemax", times = "09:00", freqType = FreqType.EVERY_N_MONTHS, freqInterval = 3,
            startDate = Dates.today().minusDays(30).toString()))
        daily = c.store.save(Medication(animalId = cat.id, name = "Flixotide", times = "08:00,20:00", startDate = Dates.today().minusDays(30).toString()))
        c.store.save(DoseLog(medicationId = quarterly.id, animalId = cat.id, takenAt = threeDaysAgo))
    }

    @Test fun quarterlyGivenThreeDaysAgoIsCoveredAndNotDue(): Unit = runBlocking {
        assertNotNull(c.doses.alreadyGiven(quarterly))
        // Un nouveau « Donné » passe par « Déjà donné ? ».
        assertTrue(c.doses.give(quarterly, com.karelisio.patoune.data.DoseStatus.GIVEN) is DoseResult.AlreadyGiven)
        val s = medStatus(c, quarterly)
        assertFalse(s.due)
        val expected = ZonedDateTime.of(Dates.localDate(threeDaysAgo).plusMonths(3), LocalTime.of(9, 0), Dates.zone()).toInstant().toEpochMilli()
        assertEquals(expected, s.nextAt)
    }

    @Test fun homeListsQuarterlyUnderUpcoming() {
        rule.setScreen { HomeScreen(it) }
        rule.awaitUntil(5_000) { rule.onAllNodes(hasText(str(R.string.home_upcoming))).fetchSemanticsNodes().isNotEmpty() }
        val dueTitle = rule.onNodeWithText(str(R.string.home_due)).fetchSemanticsNode().boundsInRoot.top
        val upcomingTitle = rule.onNodeWithText(str(R.string.home_upcoming)).fetchSemanticsNode().boundsInRoot.top
        val dailyTop = rule.onNodeWithText("Flixotide").fetchSemanticsNode().boundsInRoot.top
        val quarterlyTop = rule.onNodeWithText("Milbemax").fetchSemanticsNode().boundsInRoot.top
        // Le quotidien (non donné) est à donner, le trimestriel est en dessous, « À venir », sans bouton « Donné ».
        assertTrue(dueTitle < dailyTop && dailyTop < upcomingTitle && upcomingTitle < quarterlyTop)
        assertEquals(1, rule.onAllNodes(hasContentDescription(str(R.string.a11y_mark_given))).fetchSemanticsNodes().size)
    }
}
