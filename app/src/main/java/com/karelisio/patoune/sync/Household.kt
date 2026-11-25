package com.karelisio.patoune.sync

import com.karelisio.patoune.data.Member
import com.karelisio.patoune.data.PatouneDb
import com.karelisio.patoune.data.PushType
import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.data.Store
import com.karelisio.patoune.domain.Crypto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Contenu du QR d'invitation. La clé du foyer voyage ici, jamais par le serveur. */
@Serializable
data class Invitation(val v: Int = 1, val s: String, val i: String, val x: String, val k: String, val e: Int) {
    fun toQr(): String = "patoune:" + Json.encodeToString(serializer(), this)

    /** Code de secours : identifiant d'invitation + secret + clé, en base32 lisible (groupes de 4). */
    fun shortCode(): String {
        val bytes = hexToBytes(i) + Crypto.unb64(x) + Crypto.unb64(k) + byteArrayOf(e.toByte())
        return Crypto.base32(bytes).chunked(4).joinToString("-")
    }

    companion object {
        fun parse(text: String, defaultServer: String): Invitation? = runCatching {
            val t = text.trim()
            if (t.startsWith("patoune:")) Json.decodeFromString(serializer(), t.removePrefix("patoune:"))
            else {
                val b = Crypto.unbase32(t)
                require(b.size == 6 + 10 + 32 + 1)
                Invitation(s = defaultServer, i = bytesToHex(b.copyOfRange(0, 6)), x = Crypto.b64(b.copyOfRange(6, 16)),
                    k = Crypto.b64(b.copyOfRange(16, 48)), e = b[48].toInt() and 0xff)
            }
        }.getOrNull()

        private fun hexToBytes(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private fun bytesToHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    }
}

@Serializable
data class PushPayload(val t: String, val title: String, val body: String, val ref: String? = null, val from: String = "")

/** Création, invitation, adhésion, retrait de membre (rotation de clé) et sortie du foyer. */
class HouseholdManager(
    private val api: HouseholdApi,
    private val store: Store,
    private val db: PatouneDb,
    private val sessions: SessionStore,
    private val settings: Settings,
    private val sync: SyncEngine,
    private val fcmToken: suspend () -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Passage solo → partagé : toutes les données locales sont chiffrées et envoyées. */
    suspend fun create(name: String, server: String) {
        val kp = sessions.deviceKeys()
        val pub = Crypto.b64(kp.public.encoded)
        val r = api.createHousehold(server, pub, fcmToken())
        sessions.save(server, r.householdId, r.memberId, r.token, r.epoch, mapOf(r.epoch to Crypto.newKey()), creator = true, creatorPublic = pub)
        settings.setMemberName(name)
        store.save(Member(id = r.memberId, name = name, isCreator = true))
        store.queueSnapshot()
        sync.sync()
    }

    suspend fun invite(): Invitation {
        val s = sessions.load() ?: error("pas de foyer")
        val secret = Crypto.b64(Crypto.randomBytes(10))
        val inv = api.createInvite(sha256Hex(secret))
        return Invitation(s = s.server, i = inv.inviteId, x = secret, k = Crypto.b64(s.key), e = s.epoch)
    }

    /** Rejoindre : les données locales éventuelles sont fusionnées avec celles du foyer. */
    suspend fun join(inv: Invitation, name: String) {
        val kp = sessions.deviceKeys()
        val r = api.join(inv.s, inv.i, inv.x, Crypto.b64(kp.public.encoded), fcmToken())
        sessions.save(inv.s, r.householdId, r.memberId, r.token, inv.e, mapOf(inv.e to Crypto.unb64(inv.k)), creator = false, creatorPublic = r.creatorPublicKey)
        settings.setMemberName(name)
        store.save(Member(id = r.memberId, name = name))
        store.queueSnapshot()
        sync.sync()
    }

    /** Retrait par le créateur : nouvelle clé envoyée aux seuls membres restants (ECDH), état ré-chiffré. */
    suspend fun remove(memberId: String) {
        val s = sessions.load() ?: error("pas de foyer")
        require(s.isCreator) { "réservé au créateur" }
        runCatching { sync.sync() } // récupérer la fiche du membre avant de la marquer supprimée
        val epoch = api.removeMember(memberId)
        val newKey = Crypto.newKey()
        val me = sessions.deviceKeys()
        val wrapped = api.members().members.filter { it.id != s.memberId }.associate { m ->
            val k = Crypto.sharedKey(me.private, Crypto.publicKeyFromBytes(Crypto.unb64(m.publicKey)), "patoune-epoch-$epoch")
            m.id to Crypto.b64(Crypto.encrypt(k, newKey))
        }
        api.putKeys(epoch, wrapped)
        sessions.saveRing(epoch, s.keyring + (epoch to newKey))
        db.members().byId(memberId)?.let { store.delete(it) }
        store.queueSnapshot()
        sync.sync()
    }

    /** Quitter : retour en solo avec une copie locale complète (médias rapatriés avant). */
    suspend fun leave() {
        sessions.load()?.let { s -> runCatching { sync.downloadMedia(s) } }
        runCatching { api.leave() }
        settings.leaveHousehold()
    }

    suspend fun members(): List<Pair<RemoteMember, String>> {
        val names = db.members().allRows().filter { !it.deleted }.associate { it.id to it.name }
        return api.members().members.map { it to (names[it.id] ?: "Membre") }
    }

    /** Push chiffré vers les autres membres ; chacun filtre selon ses réglages. */
    suspend fun push(type: PushType, title: String, body: String, ref: String?) {
        val s = sessions.load() ?: return
        val p = PushPayload(type.name, title, body, ref, settings.current().memberName)
        val blob = Envelope.seal(s.key, s.epoch, json.encodeToString(PushPayload.serializer(), p).toByteArray(), "${s.householdId}|push")
        api.push(Crypto.b64(blob))
    }

    suspend fun openPush(householdId: String, data: String): PushPayload? {
        val s = sessions.load() ?: return null
        if (s.householdId != householdId) return null
        val plain = Envelope.open(s.keyring, Crypto.unb64(data), "${s.householdId}|push") ?: return null
        return json.decodeFromString(PushPayload.serializer(), plain.decodeToString())
    }

    companion object {
        fun sha256Hex(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
