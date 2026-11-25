package com.karelisio.patoune

import com.karelisio.patoune.domain.Breath
import com.karelisio.patoune.domain.PeakDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class BreathTest {
    @Test fun perMinuteForEachDuration() {
        assertEquals(24.0, Breath.perMinute(6, 15), 0.0)
        assertEquals(24.0, Breath.perMinute(12, 30), 0.0)
        assertEquals(24.0, Breath.perMinute(24, 60), 0.0)
        assertEquals(22.0, Breath.perMinute(11, 30), 0.0)
        assertEquals(0.0, Breath.perMinute(0, 30), 0.0)
    }

    @Test fun liveRateFromTapTimes() {
        val taps = (0 until 6).map { it * 2_500L } // une respiration toutes les 2,5 s
        assertEquals(24.0, Breath.liveRate(taps)!!, 0.01)
        assertNull(Breath.liveRate(listOf(0L, 1000L)))
    }

    @Test fun thresholdExceeded() {
        assertTrue(Breath.exceeds(32.0, 30))
        assertFalse(Breath.exceeds(30.0, 30))
        assertFalse(Breath.exceeds(50.0, null))
    }

    @Test fun movingAverageAndTrend() {
        assertEquals(listOf(20.0, 21.0, 22.0), Breath.movingAverage(listOf(20.0, 22.0, 24.0), window = 5))
        assertEquals(Breath.Trend.UP, Breath.trend(listOf(30.0, 22.0, 23.0, 22.0)))
        assertEquals(Breath.Trend.STABLE, Breath.trend(listOf(22.0, 22.0, 21.0)))
    }

    @Test fun peakDetectionOnSyntheticBreathing() {
        val fps = 15.0
        val bpm = 24.0
        val rnd = Random(42)
        val n = (fps * 30).toInt()
        // Mouvement du flanc : sinusoïde + dérive lente + bruit.
        val signal = DoubleArray(n) { i ->
            val t = i / fps
            sin(2 * PI * bpm / 60.0 * t) + 0.3 * t / 30 + rnd.nextDouble(-0.25, 0.25)
        }
        val est = PeakDetector.estimateBpm(signal, fps)
        assertNotNull(est)
        assertEquals(bpm, est!!, 2.0)
    }

    @Test fun peakDetectionSlowBreathing() {
        val fps = 10.0
        val signal = DoubleArray(600) { i -> sin(2 * PI * 16.0 / 60.0 * i / fps) }
        assertEquals(16.0, PeakDetector.estimateBpm(signal, fps)!!, 1.5)
    }

    @Test fun flatSignalGivesNoEstimate() {
        assertNull(PeakDetector.estimateBpm(DoubleArray(300) { 1.0 }, 15.0))
    }

    @Test fun peaksRespectMinimumDistance() {
        val x = doubleArrayOf(0.0, 1.0, 0.0, 0.9, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertEquals(listOf(1, 7), PeakDetector.peaks(x, minDistance = 4))
    }
}
