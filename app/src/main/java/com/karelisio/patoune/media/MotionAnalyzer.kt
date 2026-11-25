package com.karelisio.patoune.media

import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import com.karelisio.patoune.domain.PeakDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class AutoCount(val bpm: Double?, val seconds: Double, val signal: DoubleArray)

/**
 * Comptage auto expérimental : sur la zone du flanc choisie, différence d'images par rapport
 * à une image de référence (le mouvement respiratoire est périodique), puis détection de pics.
 */
object MotionAnalyzer {
    private const val GRID = 24

    fun firstFrame(file: File): Bitmap? = VideoCompressor.thumbnail(file, 0)

    suspend fun analyze(file: File, roi: RectF, fps: Double = 10.0, maxSeconds: Double = 30.0, onProgress: (Float) -> Unit = {}): AutoCount = withContext(Dispatchers.Default) {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(file.absolutePath)
            val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val seconds = minOf(durationMs / 1000.0, maxSeconds)
            val n = (seconds * fps).toInt()
            var ref: DoubleArray? = null
            val signal = DoubleArray(n)
            for (i in 0 until n) {
                ensureActive()
                val frame = r.getScaledFrameAtTime((i / fps * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST, 320, 320)
                val g = frame?.let { lumaGrid(it, roi) }
                frame?.recycle()
                if (g != null) {
                    if (ref == null) ref = g
                    signal[i] = g.indices.sumOf { abs(g[it] - ref!![it]) } / g.size
                } else if (i > 0) signal[i] = signal[i - 1]
                onProgress((i + 1f) / n)
            }
            AutoCount(PeakDetector.estimateBpm(signal, fps), seconds, signal)
        }
    }

    /** Luminance moyenne d'une grille GRID×GRID sur la zone (coordonnées normalisées 0..1). */
    private fun lumaGrid(b: Bitmap, roi: RectF): DoubleArray {
        val x0 = (roi.left * b.width).toInt().coerceIn(0, b.width - 1)
        val y0 = (roi.top * b.height).toInt().coerceIn(0, b.height - 1)
        val w = ((roi.width() * b.width).toInt()).coerceAtLeast(GRID).coerceAtMost(b.width - x0)
        val h = ((roi.height() * b.height).toInt()).coerceAtLeast(GRID).coerceAtMost(b.height - y0)
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, x0, y0, w, h)
        val out = DoubleArray(GRID * GRID)
        val cnt = IntArray(GRID * GRID)
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            val l = 0.299 * ((c shr 16) and 0xff) + 0.587 * ((c shr 8) and 0xff) + 0.114 * (c and 0xff)
            val k = (y * GRID / h) * GRID + (x * GRID / w)
            out[k] += l; cnt[k]++
        }
        for (k in out.indices) if (cnt[k] > 0) out[k] /= cnt[k]
        return out
    }
}
