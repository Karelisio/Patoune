package com.karelisio.patoune.domain

import kotlin.math.sqrt

/** Estimation du rythme respiratoire à partir d'un signal de mouvement (une valeur par image). */
object PeakDetector {
    fun smooth(x: DoubleArray, window: Int): DoubleArray {
        if (window <= 1) return x.copyOf()
        val out = DoubleArray(x.size)
        var sum = 0.0
        for (i in x.indices) {
            sum += x[i]
            if (i >= window) sum -= x[i - window]
            out[i] = sum / minOf(i + 1, window)
        }
        return out
    }

    fun detrend(x: DoubleArray, window: Int): DoubleArray {
        val base = smooth(x, window)
        return DoubleArray(x.size) { x[it] - base[it] }
    }

    /**
     * Indices des pics : maximum local, au-dessus de moyenne + k·écart-type,
     * espacés d'au moins [minDistance] échantillons.
     */
    fun peaks(x: DoubleArray, minDistance: Int, k: Double = 0.3): List<Int> {
        if (x.size < 3) return emptyList()
        val mean = x.average()
        val sd = sqrt(x.sumOf { (it - mean) * (it - mean) } / x.size)
        val threshold = mean + k * sd
        val result = ArrayList<Int>()
        for (i in 1 until x.size - 1) {
            if (x[i] > threshold && x[i] >= x[i - 1] && x[i] > x[i + 1]) {
                if (result.isNotEmpty() && i - result.last() < minDistance) {
                    if (x[i] > x[result.last()]) result[result.size - 1] = i
                } else result += i
            }
        }
        return result
    }

    /** Rythme estimé (resp/min) pour un signal échantillonné à [fps]. Respirations entre 8 et 120/min. */
    fun estimateBpm(signal: DoubleArray, fps: Double): Double? {
        if (signal.size < fps * 5) return null
        val clean = smooth(detrend(signal, (fps * 3).toInt()), maxOf(1, (fps / 6).toInt()))
        val minDist = maxOf(1, (fps * 60 / 120).toInt())
        val p = peaks(clean, minDist)
        if (p.size < 2) return null
        val intervals = p.zipWithNext { a, b -> (b - a) / fps }.sorted()
        val median = intervals[intervals.size / 2]
        val bpm = 60.0 / median
        return bpm.takeIf { it in 8.0..120.0 }
    }
}
