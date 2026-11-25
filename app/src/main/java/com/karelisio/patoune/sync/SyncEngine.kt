package com.karelisio.patoune.sync

import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.domain.Crypto
import com.karelisio.patoune.domain.FieldEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class SyncReport(val pushed: Int, val pulled: Int, val applied: Int, val mediaUp: Int, val mediaDown: Int)

/**
 * Synchronisation par journal d'événements chiffrés :
 * 1) mise à jour du trousseau si la clé a tourné, 2) envoi de la file locale, 3) réception et fusion
 * (dernier écrit gagne par champ), 4) médias chiffrés, 5) quota.
 *
 * Médias (format v2) : le fichier est lu et envoyé par morceaux de 1 Mio, chacun scellé à part
 * ([epoch][AES-GCM(morceau)], AAD `householdId|media|id|index|total` : ni réordonnancement ni troncature).
 * La progression (morceaux confirmés) est gardée hors base, dans [progressDir], pour reprendre après une coupure.
 */
class SyncEngine(
    private val api: HouseholdApi,
    private val store: Store,
    private val db: PatouneDb,
    private val sessions: SessionStore,
    private val settings: Settings,
    private val mediaDir: () -> File,
    /** Progression des envois en cours, un fichier par média (par défaut filesDir/upload-progress). */
    private val progressDir: () -> File = { File(mediaDir().parentFile ?: mediaDir(), "upload-progress") },
    private val onRemoteApplied: suspend (List<FieldEvent>) -> Unit = {},
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val listSer = ListSerializer(FieldEvent.serializer())

    suspend fun sync(): SyncReport? = mutex.withLock {
        var s = sessions.load() ?: return null
        s = refreshKeys(s)
        val pushed = push(s)
        val (pulled, applied) = pull(s)
        val up = uploadMedia(s)
        val down = downloadMedia(s)
        runCatching { api.quota() }.getOrNull()?.let { settings.setQuota(it.used, it.max) }
        settings.setLastSync(System.currentTimeMillis())
        SyncReport(pushed, pulled, applied, up, down)
    }

    /** Après le retrait d'un membre, récupère la nouvelle clé enveloppée pour moi (ECDH). */
    suspend fun refreshKeys(s: Session): Session {
        val k = api.keys()
        if (k.epoch <= s.epoch || s.keyring.containsKey(k.epoch)) return s
        val wrapped = k.wrapped ?: return s
        val creatorPub = k.creatorPublicKey ?: s.creatorPublicKey ?: return s
        val shared = Crypto.sharedKey(sessions.deviceKeys().private, Crypto.publicKeyFromBytes(Crypto.unb64(creatorPub)), "patoune-epoch-${k.epoch}")
        val key = runCatching { Crypto.decrypt(shared, Crypto.unb64(wrapped)) }.getOrNull() ?: return s
        val ring = s.keyring + (k.epoch to key)
        sessions.saveRing(k.epoch, ring)
        return s.copy(epoch = k.epoch, keyring = ring)
    }

    private suspend fun push(s: Session): Int {
        var total = 0
        while (true) {
            val pending = store.pending(400)
            if (pending.isEmpty()) break
            val events = pending.map(store::toFieldEvent)
            val id = Crypto.b64(Crypto.randomBytes(16))
            val blob = Envelope.seal(s.key, s.epoch, json.encodeToString(listSer, events).toByteArray(), aad(s, "blob", id))
            api.pushBlobs(listOf(BlobOut(id, s.epoch, Crypto.b64(blob))))
            store.markPushed(pending.map { it.eventId })
            total += pending.size
        }
        return total
    }

    private suspend fun pull(s: Session): Pair<Int, Int> {
        var cursor = settings.current().syncCursor
        var pulled = 0; var applied = 0
        while (true) {
            val resp = api.blobs(cursor, LIMIT)
            for (b in resp.blobs) {
                val plain = Envelope.open(s.keyring, Crypto.unb64(b.data), aad(s, "blob", b.id))
                if (plain != null) {
                    val events = json.decodeFromString(listSer, plain.decodeToString())
                    val n = store.applyRemote(events)
                    applied += n
                    if (n > 0) onRemoteApplied(events)
                    pulled += events.size
                }
                cursor = b.seq
            }
            settings.setCursor(cursor)
            if (resp.blobs.size < LIMIT) break
        }
        return pulled to applied
    }

    private suspend fun uploadMedia(s: Session): Int = withContext(Dispatchers.IO) {
        val pending = db.media().pendingUpload()
        if (pending.isEmpty()) return@withContext 0
        val maxMedia = runCatching { api.quota().maxMedia }.getOrNull()
        var n = 0
        for (m in pending) {
            val f = File(mediaDir(), m.id).takeIf { it.isFile } ?: continue
            // Trop gros pour le serveur : inutile d'envoyer des morceaux qui seraient refusés.
            if (maxMedia != null && encryptedSize(f.length()) > maxMedia) continue
            try {
                uploadChunked(s, m.id, f)
                store.saveLocal(m.copy(uploaded = true))
                progressFile(m.id).delete()
                n++
            } catch (e: ApiException) {
                when (e.status) {
                    // Trop gros ou quota atteint : le média reste sur ce téléphone, la sync continue
                    // (progression gardée : l'envoi reprendra là où il en était quand de la place se libère).
                    413, 507 -> Unit
                    // Le serveur n'a pas tous les morceaux annoncés : on repartira du premier.
                    409 -> progressFile(m.id).delete()
                    else -> throw e
                }
            }
        }
        n
    }

    /** Envoie les morceaux pas encore confirmés (lecture en flux, un morceau en mémoire), puis termine le média. */
    private suspend fun uploadChunked(s: Session, id: String, f: File) {
        val size = f.length()
        val total = partCount(size)
        val progress = progressFile(id)
        val done = readProgress(progress, size, total)
        RandomAccessFile(f, "r").use { raf ->
            val buf = ByteArray(CHUNK)
            for (i in done until total) {
                val offset = i.toLong() * CHUNK
                val len = minOf(CHUNK.toLong(), size - offset).toInt()
                raf.seek(offset)
                raf.readFully(buf, 0, len)
                val sealed = Envelope.seal(s.key, s.epoch, buf.copyOf(len), partAad(s, id, i, total), compress = false)
                api.putMediaPart(id, i, sealed)
                writeProgress(progress, size, total, i + 1)
            }
        }
        api.completeMedia(id, total, encryptedSize(size))
    }

    private fun progressFile(id: String) = File(progressDir(), id)

    /** Nombre de morceaux confirmés ; 0 si absent, illisible ou si le fichier a changé depuis. */
    private fun readProgress(p: File, size: Long, total: Int): Int {
        if (!p.isFile) return 0
        val v = runCatching { p.readText().trim().split(' ').map(String::toLong) }.getOrNull() ?: return 0
        if (v.size != 3 || v[0] != size || v[1] != total.toLong()) return 0
        return v[2].toInt().coerceIn(0, total)
    }

    /** Écriture atomique (fichier temporaire puis renommage) : une coupure ne laisse jamais un compte faux. */
    private fun writeProgress(p: File, size: Long, total: Int, done: Int) {
        val dir = p.parentFile ?: return
        dir.mkdirs()
        val tmp = File(dir, "${p.name}.tmp")
        tmp.writeText("$size $total $done")
        if (!tmp.renameTo(p)) { p.delete(); tmp.renameTo(p) }
    }

    suspend fun downloadMedia(s: Session): Int = withContext(Dispatchers.IO) {
        var n = 0
        for (m in db.media().missingLocal()) {
            val info = api.mediaInfo(m.id)
            val f = File(mediaDir(), m.id)
            val tmp = File(mediaDir(), "${m.id}.download")
            val ok = try {
                // Pas d'info (serveur pas encore migré) ou parts = 0 : ancien format, un seul blob.
                if (info == null || info.parts == 0) fetchLegacy(s, m.id, tmp) else fetchChunked(s, m.id, info.parts, tmp)
            } catch (e: Exception) {
                tmp.delete(); throw e
            }
            if (!ok || !(tmp.renameTo(f) || (f.delete() && tmp.renameTo(f)))) { tmp.delete(); continue }
            // Déjà sur le serveur : ce téléphone n'a pas à le renvoyer.
            store.saveLocal(m.copy(localPath = f.absolutePath, uploaded = true))
            n++
        }
        n
    }

    private suspend fun fetchLegacy(s: Session, id: String, out: File): Boolean {
        val blob = api.getMedia(id) ?: return false
        val plain = Envelope.open(s.keyring, blob, aad(s, "media", id), compressed = false) ?: return false
        out.writeBytes(plain)
        return true
    }

    /** Déchiffre chaque morceau en vérifiant son AAD (index et total) et l'écrit à la suite, sans tout garder en mémoire. */
    private suspend fun fetchChunked(s: Session, id: String, total: Int, out: File): Boolean =
        FileOutputStream(out).use { o ->
            for (i in 0 until total) {
                val blob = api.getMediaPart(id, i) ?: return@use false
                val plain = Envelope.open(s.keyring, blob, partAad(s, id, i, total), compressed = false) ?: return@use false
                o.write(plain)
            }
            true
        }

    companion object {
        const val LIMIT = 200
        /** Taille d'un morceau de média en clair (1 Mio). */
        const val CHUNK = 1 shl 20
        /** Surcoût par morceau chiffré : epoch (2) + nonce (12) + tag AES-GCM (16). */
        const val PART_OVERHEAD = 2 + 12 + 16
        fun aad(s: Session, kind: String, id: String) = "${s.householdId}|$kind|$id"
        fun partAad(s: Session, id: String, index: Int, total: Int) = aad(s, "media", "$id|$index|$total")
        /** Nombre de morceaux (au moins un, même pour un fichier vide). */
        fun partCount(size: Long): Int = maxOf(1L, (size + CHUNK - 1) / CHUNK).toInt()
        fun encryptedSize(size: Long): Long = size + partCount(size).toLong() * PART_OVERHEAD
    }
}
