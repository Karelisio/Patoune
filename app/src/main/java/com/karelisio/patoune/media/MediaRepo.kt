package com.karelisio.patoune.media

import android.content.Context
import android.net.Uri
import com.karelisio.patoune.data.AttachType
import com.karelisio.patoune.data.MediaItem
import com.karelisio.patoune.data.MediaKind
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.domain.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fichiers médias dans filesDir/media/<id> (stockage privé de l'appli). */
class MediaRepo(private val context: Context, private val store: Store) {
    val dir: File get() = File(context.filesDir, "media").apply { mkdirs() }

    fun file(id: String): File = File(dir, id)

    fun newFile(): Pair<String, File> = newId().let { it to file(it) }

    suspend fun register(id: String, animalId: String, kind: MediaKind, mime: String, attach: AttachType, attachId: String?, durationMs: Long = 0, takenAt: Long = System.currentTimeMillis()): MediaItem {
        val f = file(id)
        return store.save(MediaItem(id = id, animalId = animalId, kind = kind, mime = mime, sizeBytes = f.length(), durationMs = durationMs,
            attachedType = attach, attachedId = attachId, takenAt = takenAt, localPath = f.absolutePath))
    }

    /** Copie un contenu (Photo Picker, document) dans l'espace privé. */
    suspend fun import(uri: Uri, animalId: String, attach: AttachType, attachId: String?): MediaItem? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val kind = when {
            mime.startsWith("video") -> MediaKind.VIDEO
            mime == "application/pdf" -> MediaKind.PDF
            else -> MediaKind.PHOTO
        }
        val (id, f) = newFile()
        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } } ?: return@withContext null
        if (kind == MediaKind.VIDEO) {
            val compressed = VideoCompressor.compress(context, f)
            if (compressed != null) { f.delete(); compressed.renameTo(f) }
        }
        register(id, animalId, kind, if (kind == MediaKind.VIDEO) "video/mp4" else mime, attach, attachId,
            durationMs = if (kind == MediaKind.VIDEO) VideoCompressor.durationMs(f) else 0)
    }

    suspend fun delete(item: MediaItem) {
        store.delete(item)
        withContext(Dispatchers.IO) { file(item.id).delete() }
    }
}
