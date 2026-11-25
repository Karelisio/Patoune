package com.karelisio.patoune

import com.karelisio.patoune.sync.ApiException
import com.karelisio.patoune.sync.BlobIn
import com.karelisio.patoune.sync.BlobOut
import com.karelisio.patoune.sync.BlobsResp
import com.karelisio.patoune.sync.CreateResp
import com.karelisio.patoune.sync.HouseholdApi
import com.karelisio.patoune.sync.HouseholdManager
import com.karelisio.patoune.sync.InviteResp
import com.karelisio.patoune.sync.JoinResp
import com.karelisio.patoune.sync.KeysResp
import com.karelisio.patoune.sync.MediaInfo
import com.karelisio.patoune.sync.MembersResp
import com.karelisio.patoune.sync.Quota
import com.karelisio.patoune.sync.RemoteMember
import java.io.IOException
import java.util.UUID

/** Réplique en mémoire du Worker (mêmes règles), pour tester la sync sans réseau. */
class FakeServer {
    data class M(val id: String, val hid: String, val token: String, val pub: String, var creator: Boolean, val joined: Long)
    val households = HashMap<String, Int>()
    val members = ArrayList<M>()
    val invites = HashMap<String, Triple<String, String, Boolean>>()
    val blobs = ArrayList<Triple<String, Long, BlobIn>>()
    val wrapped = HashMap<Triple<String, Int, String>, String>()
    /** Médias à l'ancien format (un seul blob). */
    val media = HashMap<Pair<String, String>, ByteArray>()
    /** Format v2 : morceaux chiffrés (foyer, id, index) et médias terminés. */
    val parts = HashMap<Triple<String, String, Int>, ByteArray>()
    val completed = HashMap<Pair<String, String>, MediaInfo>()
    /** Morceaux acceptés, dans l'ordre de réception (pour vérifier la reprise). */
    val partUploads = ArrayList<Pair<String, Int>>()
    /** Si renvoie vrai pour (id, index), l'envoi de ce morceau échoue une fois (coupure réseau). */
    var failPart: ((String, Int) -> Boolean)? = null
    var maxMedia: Long = 200L shl 20
    val pushes = ArrayList<String>()
    /** Tout ce que le serveur a reçu, pour vérifier qu'aucune donnée n'arrive en clair. */
    val received = StringBuilder()
    private var seq = 0L
    private var clock = 0L

    fun client(token: () -> String?): HouseholdApi = Client(token)

    private companion object {
        /** Comme le Worker : ~1,1 Mio par morceau chiffré. */
        const val MAX_PART = 1_153_433
    }

    private inner class Client(private val tokenOf: () -> String?) : HouseholdApi {
        private fun me(): M = members.firstOrNull { it.token == tokenOf() } ?: throw ApiException(401, "unknown token")
        private fun newMember(hid: String, pub: String, creator: Boolean): M =
            M(UUID.randomUUID().toString().take(12), hid, UUID.randomUUID().toString(), pub, creator, clock++).also { members += it }

        override suspend fun createHousehold(server: String, publicKey: String, fcmToken: String?): CreateResp {
            val hid = UUID.randomUUID().toString(); households[hid] = 1
            val m = newMember(hid, publicKey, true)
            return CreateResp(hid, m.id, m.token, 1)
        }
        override suspend fun join(server: String, inviteId: String, secret: String, publicKey: String, fcmToken: String?): JoinResp {
            val inv = invites[inviteId] ?: throw ApiException(403, "invite")
            if (inv.third || inv.second != HouseholdManager.sha256Hex(secret)) throw ApiException(403, "invite")
            if (members.count { it.hid == inv.first } >= 6) throw ApiException(409, "full")
            invites[inviteId] = inv.copy(third = true)
            val m = newMember(inv.first, publicKey, false)
            return JoinResp(inv.first, m.id, m.token, households[inv.first]!!, members.first { it.hid == inv.first && it.creator }.pub)
        }
        override suspend fun createInvite(secretHash: String): InviteResp {
            val id = UUID.randomUUID().toString().replace("-", "").take(12)
            invites[id] = Triple(me().hid, secretHash, false)
            return InviteResp(id, Long.MAX_VALUE)
        }
        override suspend fun members(): MembersResp {
            val m = me()
            return MembersResp(households[m.hid]!!, members.filter { it.hid == m.hid }.map { RemoteMember(it.id, it.pub, it.creator, it.joined) })
        }
        override suspend fun removeMember(id: String): Int {
            val m = me(); if (!m.creator) throw ApiException(403, "creator only")
            members.removeAll { it.id == id && it.hid == m.hid }
            households[m.hid] = households[m.hid]!! + 1
            return households[m.hid]!!
        }
        override suspend fun leave() {
            val m = me(); members.remove(m)
            if (m.creator) members.filter { it.hid == m.hid }.minByOrNull { it.joined }?.creator = true
        }
        override suspend fun putKeys(epoch: Int, wrapped: Map<String, String>) {
            val m = me(); wrapped.forEach { (k, v) -> this@FakeServer.wrapped[Triple(m.hid, epoch, k)] = v; received.append(v) }
        }
        override suspend fun keys(): KeysResp {
            val m = me(); val e = households[m.hid]!!
            return KeysResp(e, wrapped[Triple(m.hid, e, m.id)], members.firstOrNull { it.hid == m.hid && it.creator }?.pub)
        }
        override suspend fun pushBlobs(blobs: List<BlobOut>): Long {
            val m = me()
            for (b in blobs) if (this@FakeServer.blobs.none { it.first == m.hid && it.third.id == b.id }) {
                this@FakeServer.blobs += Triple(m.hid, ++seq, BlobIn(seq, b.id, b.epoch, b.data)); received.append(b.data)
            }
            return seq
        }
        override suspend fun blobs(after: Long, limit: Int): BlobsResp {
            val m = me()
            return BlobsResp(households[m.hid]!!, blobs.filter { it.first == m.hid && it.second > after }.take(limit).map { it.third })
        }
        override suspend fun putMedia(id: String, bytes: ByteArray) {
            val hid = me().hid
            media[hid to id] = bytes; completed.remove(hid to id); parts.keys.removeAll { it.first == hid && it.second == id }
            received.append(String(bytes, Charsets.ISO_8859_1))
        }
        override suspend fun getMedia(id: String): ByteArray? = media[me().hid to id]
        override suspend fun putMediaPart(id: String, index: Int, bytes: ByteArray) {
            val hid = me().hid
            if (failPart?.invoke(id, index) == true) { failPart = null; throw IOException("coupure réseau simulée") }
            if (bytes.size > MAX_PART) throw ApiException(413, "too large")
            val others = parts.filterKeys { it.first == hid && it.second == id && it.third != index }.values.sumOf { it.size.toLong() }
            if (others + bytes.size > maxMedia) throw ApiException(413, "too large")
            parts[Triple(hid, id, index)] = bytes
            partUploads += id to index
            received.append(String(bytes, Charsets.ISO_8859_1))
        }
        override suspend fun completeMedia(id: String, parts: Int, size: Long) {
            val hid = me().hid
            val got = (0 until parts).map { this@FakeServer.parts[Triple(hid, id, it)] ?: throw ApiException(409, "missing parts") }
            if (got.sumOf { it.size.toLong() } != size) throw ApiException(409, "size")
            this@FakeServer.parts.keys.removeAll { it.first == hid && it.second == id && it.third >= parts }
            media.remove(hid to id)
            completed[hid to id] = MediaInfo(parts, size)
        }
        override suspend fun mediaInfo(id: String): MediaInfo? {
            val hid = me().hid
            return completed[hid to id] ?: media[hid to id]?.let { MediaInfo(0, it.size.toLong()) }
        }
        override suspend fun getMediaPart(id: String, index: Int): ByteArray? = parts[Triple(me().hid, id, index)]
        override suspend fun quota(): Quota {
            val hid = me().hid
            val used = media.filterKeys { it.first == hid }.values.sumOf { it.size.toLong() } +
                parts.filterKeys { it.first == hid }.values.sumOf { it.size.toLong() }
            return Quota(used, 1L shl 30, maxMedia)
        }
        override suspend fun push(data: String) { me(); pushes += data; received.append(data) }
        override suspend fun setFcmToken(token: String?) { me() }
    }
}
