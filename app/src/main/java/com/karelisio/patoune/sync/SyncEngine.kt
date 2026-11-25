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

data class SyncReport(val pushed: Int, val pulled: Int, val applied: Int, val mediaUp: Int, val mediaDown: Int)

/**
 * Synchronisation par journal d'événements chiffrés :
 * 1) mise à jour du trousseau si la clé a tourné, 2) envoi de la file locale, 3) réception et fusion
 * (dernier écrit gagne par champ), 4) médias chiffrés, 5) quota.
 */
class SyncEngine(
    private val api: HouseholdApi,
    private val store: Store,
    private val db: PatouneDb,
    private val sessions: SessionStore,
    private val settings: Settings,
    private val mediaDir: () -> File,
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
        var n = 0
        for (m in db.media().pendingUpload()) {
            val f = File(mediaDir(), m.id).takeIf { it.exists() } ?: continue
            val blob = Envelope.seal(s.key, s.epoch, f.readBytes(), aad(s, "media", m.id), compress = false)
            try {
                api.putMedia(m.id, blob)
                store.saveLocal(m.copy(uploaded = true))
                n++
            } catch (e: ApiException) {
                // Trop gros ou quota atteint : le média reste sur ce téléphone, la sync continue.
                if (e.status != 413 && e.status != 507) throw e
            }
        }
        n
    }

    suspend fun downloadMedia(s: Session): Int = withContext(Dispatchers.IO) {
        var n = 0
        for (m in db.media().missingLocal()) {
            val blob = api.getMedia(m.id) ?: continue
            val plain = Envelope.open(s.keyring, blob, aad(s, "media", m.id), compressed = false) ?: continue
            val f = File(mediaDir(), m.id)
            f.writeBytes(plain)
            store.saveLocal(m.copy(localPath = f.absolutePath))
            n++
        }
        n
    }

    companion object {
        const val LIMIT = 200
        fun aad(s: Session, kind: String, id: String) = "${s.householdId}|$kind|$id"
    }
}
