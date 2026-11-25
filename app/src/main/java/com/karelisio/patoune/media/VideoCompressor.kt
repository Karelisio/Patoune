package com.karelisio.patoune.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Compression des vidéos en 720p H.264 (Media3 Transformer) pour limiter la place et la sync. */
object VideoCompressor {

    @OptIn(UnstableApi::class)
    suspend fun compress(context: Context, input: File): File? {
        val height = withContext(Dispatchers.IO) { videoHeight(input) }
        if (height != null && height <= 720) return null
        val out = File(input.parentFile, input.name + ".720.mp4")
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) { cont.resume(out) }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            out.delete(); cont.resume(null)
                        }
                    })
                    .build()
                val item = EditedMediaItem.Builder(MediaItem.fromUri(input.toURI().toString()))
                    .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720))))
                    .build()
                transformer.start(item, out.absolutePath)
                cont.invokeOnCancellation { transformer.cancel(); out.delete() }
            }
        }
    }

    private fun videoHeight(f: File): Int? = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(f.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
            minOf(w, h)
        }
    }.getOrNull()

    fun durationMs(f: File): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        }
    }.getOrDefault(0)

    fun thumbnail(f: File, atMs: Long = 0): Bitmap? = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(f.absolutePath)
            r.getScaledFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 480)
        }
    }.getOrNull()
}
