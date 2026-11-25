package com.karelisio.patoune.domain

import kotlin.math.roundToInt

object Breath {
    val durations = listOf(15, 30, 60)

    /** Respirations par minute à partir du nombre de taps sur une durée donnée. */
    fun perMinute(taps: Int, durationSec: Int): Double {
        require(durationSec > 0) { "durée nulle" }
        return taps * 60.0 / durationSec
    }

    /** Estimation en direct à partir des instants de tap (ms), avant la fin du chrono. */
    fun liveRate(tapTimes: List<Long>): Double? {
        if (tapTimes.size < 3) return null
        val span = tapTimes.last() - tapTimes.first()
        if (span <= 0) return null
        return (tapTimes.size - 1) * 60_000.0 / span
    }

    fun exceeds(bpm: Double, threshold: Int?): Boolean = threshold != null && bpm > threshold

    /** Moyenne mobile sur une fenêtre de [window] mesures. */
    fun movingAverage(values: List<Double>, window: Int = 5): List<Double> =
        values.indices.map { i ->
            val from = maxOf(0, i - window + 1)
            values.subList(from, i + 1).average()
        }

    enum class Trend { UP, DOWN, STABLE }

    fun trend(recentFirst: List<Double>): Trend {
        if (recentFirst.size < 2) return Trend.STABLE
        val last = recentFirst.first()
        val prev = recentFirst.drop(1).take(4).average()
        val delta = last - prev
        return when {
            delta >= 2 -> Trend.UP
            delta <= -2 -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    fun format(bpm: Double): String = bpm.roundToInt().toString()
}
