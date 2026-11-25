package com.karelisio.patoune.backup

import com.karelisio.patoune.data.MediaItem
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.domain.Crypto
import com.karelisio.patoune.domain.FieldEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException

@Serializable
data class BackupHeader(val format: Int = 1, val app: String = "Patoune", val salt: String, val iterations: Int, val createdAt: Long)

data class ImportResult(val rows: Int, val media: Int)

class WrongPassword : Exception("Mot de passe incorrect")

/**
 * Sauvegarde chiffrée : un .zip dont chaque entrée (données, médias) est chiffrée en AES-GCM
 * avec une clé dérivée du mot de passe (PBKDF2). L'import fusionne avec la règle « dernier écrit gagne ».
 */
class Backup(private val db: PatouneDb, private val store: Store, private val mediaDir: () -> File) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(out: OutputStream, password: CharArray, iterations: Int = 120_000) = withContext(Dispatchers.IO) {
        val salt = Crypto.randomBytes(16)
        val key = Crypto.deriveFromPassword(password, salt, iterations)
        val events = store.snapshotEvents()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(HEADER))
            zip.write(json.encodeToString(BackupHeader.serializer(), BackupHeader(salt = Crypto.b64(salt), iterations = iterations, createdAt = System.currentTimeMillis())).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(Crypto.encrypt(key, json.encodeToString(ListSerializer(FieldEvent.serializer()), events).toByteArray(), DATA.toByteArray()))
            zip.closeEntry()
            for (m in db.media().allRows().filter { !it.deleted }) {
                val f = File(mediaDir(), m.id)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("media/${m.id}.enc"))
                zip.write(Crypto.encrypt(key, f.readBytes(), m.id.toByteArray()))
                zip.closeEntry()
            }
        }
    }

    suspend fun import(input: InputStream, password: CharArray): ImportResult = withContext(Dispatchers.IO) {
        var key: ByteArray? = null
        var events: List<FieldEvent> = emptyList()
        val files = HashMap<String, File>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                when {
                    e.name == HEADER -> {
                        val h = json.decodeFromString(BackupHeader.serializer(), bytes.decodeToString())
                        key = Crypto.deriveFromPassword(password, Crypto.unb64(h.salt), h.iterations)
                    }
                    e.name == DATA -> {
                        val plain = try { Crypto.decrypt(requireNotNull(key), bytes, DATA.toByteArray()) } catch (_: AEADBadTagException) { throw WrongPassword() }
                        events = json.decodeFromString(ListSerializer(FieldEvent.serializer()), plain.decodeToString())
                    }
                    e.name.startsWith("media/") -> {
                        val id = e.name.removePrefix("media/").removeSuffix(".enc")
                        if (id.contains('/') || id.contains("..")) continue
                        val f = File(mediaDir(), id)
                        f.writeBytes(Crypto.decrypt(requireNotNull(key), bytes, id.toByteArray()))
                        files[id] = f
                    }
                }
            }
        }
        val applied = store.applyRemote(events)
        for ((id, f) in files) {
            db.media().byId(id)?.let { m: MediaItem -> store.saveLocal(m.copy(localPath = f.absolutePath)) }
        }
        ImportResult(applied, files.size)
    }

    companion object {
        const val HEADER = "patoune-backup.json"
        const val DATA = "data.enc"
    }
}
