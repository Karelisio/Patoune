package com.karelisio.patoune

import com.karelisio.patoune.update.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun semverComparison() {
        assertTrue(Updater.isNewer("0.2.0", "0.1.9"))
        assertTrue(Updater.isNewer("1.0.0", "0.9.9"))
        assertFalse(Updater.isNewer("0.1.0", "0.1.0"))
        assertFalse(Updater.isNewer("0.0.9", "0.1.0"))
        assertTrue(Updater.isNewer("0.1.10", "0.1.9"))
    }
}
