package com.karelisio.patoune.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable data class CreateResp(val householdId: String, val memberId: String, val token: String, val epoch: Int)
@Serializable data class JoinResp(val householdId: String, val memberId: String, val token: String, val epoch: Int, val creatorPublicKey: String? = null)
@Serializable data class InviteResp(val inviteId: String, val expiresAt: Long)
@Serializable data class RemoteMember(val id: String, val publicKey: String, val isCreator: Boolean, val joinedAt: Long)
@Serializable data class MembersResp(val epoch: Int, val members: List<RemoteMember>)
@Serializable data class KeysResp(val epoch: Int, val wrapped: String? = null, val creatorPublicKey: String? = null)
@Serializable data class BlobOut(val id: String, val epoch: Int, val data: String)
@Serializable data class BlobIn(val seq: Long, val id: String, val epoch: Int, val data: String)
@Serializable data class BlobsResp(val epoch: Int, val blobs: List<BlobIn>)
@Serializable data class Quota(val used: Long, val max: Long, val maxMedia: Long)
/** État d'un média sur le serveur : parts = 0 pour l'ancien format (un seul blob), sinon nombre de morceaux. */
@Serializable data class MediaInfo(val parts: Int, val size: Long, val format: Int = if (parts > 0) 2 else 1)

class ApiException(val status: Int, message: String) : IOException("HTTP $status : $message")

/** Accès au Worker. Uniquement instancié en mode partagé : en solo, aucun appel réseau n'est fait. */
interface HouseholdApi {
    suspend fun createHousehold(server: String, publicKey: String, fcmToken: String?): CreateResp
    suspend fun join(server: String, inviteId: String, secret: String, publicKey: String, fcmToken: String?): JoinResp
    suspend fun createInvite(secretHash: String): InviteResp
    suspend fun members(): MembersResp
    suspend fun removeMember(id: String): Int
    suspend fun leave()
    suspend fun putKeys(epoch: Int, wrapped: Map<String, String>)
    suspend fun keys(): KeysResp
    suspend fun pushBlobs(blobs: List<BlobOut>): Long
    suspend fun blobs(after: Long, limit: Int = 200): BlobsResp
    /** Ancien format (v1) : média chiffré d'un seul bloc. Conservé pour relire les médias déjà envoyés. */
    suspend fun putMedia(id: String, bytes: ByteArray)
    suspend fun getMedia(id: String): ByteArray?
    /** Format v2 : un morceau chiffré (idempotent : renvoyer le même index le remplace). */
    suspend fun putMediaPart(id: String, index: Int, bytes: ByteArray)
    /** Enregistre le média une fois les morceaux 0..parts-1 reçus ; size = total chiffré (vérifié par le serveur). */
    suspend fun completeMedia(id: String, parts: Int, size: Long)
    /** null si le média n'existe pas (encore) sur le serveur. */
    suspend fun mediaInfo(id: String): MediaInfo?
    suspend fun getMediaPart(id: String, index: Int): ByteArray?
    suspend fun quota(): Quota
    suspend fun push(data: String)
    suspend fun setFcmToken(token: String?)
}

/** Implémentation HTTP (HttpURLConnection, pas de dépendance réseau supplémentaire). */
class HttpApi(private val session: suspend () -> Pair<String, String>?) : HouseholdApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private suspend fun raw(method: String, url: String, token: String?, body: ByteArray?, contentType: String = "application/json"): ByteArray? = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        if (code == 404 && method == "GET" && url.contains("/v1/media/")) return@withContext null
        if (code !in 200..299) throw ApiException(code, c.errorStream?.bufferedReader()?.readText().orEmpty())
        c.inputStream.use { it.readBytes() }
    }

    private suspend fun authed(): Pair<String, String> = session() ?: throw ApiException(401, "pas de foyer")

    private suspend inline fun <reified T> call(method: String, path: String, body: String? = null, server: String? = null, auth: Boolean = true): T {
        val (srv, token) = if (auth) authed() else (server!! to "")
        val payload = body?.toByteArray()
        val res = raw(method, srv.trimEnd('/') + path, token.ifEmpty { null }, payload)!!
        return json.decodeFromString(res.decodeToString())
    }

    private fun obj(vararg pairs: Pair<String, Any?>): String = kotlinx.serialization.json.buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                null -> put(k, kotlinx.serialization.json.JsonNull)
                is String -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
                is Number -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
                is kotlinx.serialization.json.JsonElement -> put(k, v)
            }
        }
    }.toString()

    override suspend fun createHousehold(server: String, publicKey: String, fcmToken: String?): CreateResp =
        call("POST", "/v1/households", obj("publicKey" to publicKey, "fcmToken" to fcmToken), server, auth = false)

    override suspend fun join(server: String, inviteId: String, secret: String, publicKey: String, fcmToken: String?): JoinResp =
        call("POST", "/v1/invites/$inviteId/join", obj("secret" to secret, "publicKey" to publicKey, "fcmToken" to fcmToken), server, auth = false)

    override suspend fun createInvite(secretHash: String): InviteResp = call("POST", "/v1/invites", obj("secretHash" to secretHash))
    override suspend fun members(): MembersResp = call("GET", "/v1/members")
    override suspend fun removeMember(id: String): Int =
        call<kotlinx.serialization.json.JsonObject>("DELETE", "/v1/members/$id")["epoch"].toString().toInt()
    override suspend fun leave() { call<kotlinx.serialization.json.JsonObject>("DELETE", "/v1/me") }
    override suspend fun putKeys(epoch: Int, wrapped: Map<String, String>) {
        val w = kotlinx.serialization.json.JsonObject(wrapped.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) })
        call<kotlinx.serialization.json.JsonObject>("POST", "/v1/keys", obj("epoch" to epoch, "wrapped" to w))
    }
    override suspend fun keys(): KeysResp = call("GET", "/v1/keys")
    override suspend fun pushBlobs(blobs: List<BlobOut>): Long {
        val arr = json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(BlobOut.serializer()), blobs)
        return call<kotlinx.serialization.json.JsonObject>("POST", "/v1/blobs", obj("blobs" to arr))["seq"].toString().toLong()
    }
    override suspend fun blobs(after: Long, limit: Int): BlobsResp = call("GET", "/v1/blobs?after=$after&limit=$limit")
    override suspend fun putMedia(id: String, bytes: ByteArray) {
        val (srv, token) = authed()
        raw("PUT", "${srv.trimEnd('/')}/v1/media/$id", token, bytes, "application/octet-stream")
    }
    override suspend fun getMedia(id: String): ByteArray? {
        val (srv, token) = authed()
        return raw("GET", "${srv.trimEnd('/')}/v1/media/$id", token, null)
    }
    override suspend fun putMediaPart(id: String, index: Int, bytes: ByteArray) {
        val (srv, token) = authed()
        raw("PUT", "${srv.trimEnd('/')}/v1/media/$id/parts/$index", token, bytes, "application/octet-stream")
    }
    override suspend fun completeMedia(id: String, parts: Int, size: Long) {
        call<kotlinx.serialization.json.JsonObject>("POST", "/v1/media/$id/complete", obj("parts" to parts, "size" to size))
    }
    override suspend fun mediaInfo(id: String): MediaInfo? {
        val (srv, token) = authed()
        val res = raw("GET", "${srv.trimEnd('/')}/v1/media/$id/info", token, null) ?: return null
        return json.decodeFromString(res.decodeToString())
    }
    override suspend fun getMediaPart(id: String, index: Int): ByteArray? {
        val (srv, token) = authed()
        return raw("GET", "${srv.trimEnd('/')}/v1/media/$id/parts/$index", token, null)
    }
    override suspend fun quota(): Quota = call("GET", "/v1/quota")
    override suspend fun push(data: String) { call<kotlinx.serialization.json.JsonObject>("POST", "/v1/push", obj("data" to data)) }
    override suspend fun setFcmToken(token: String?) { call<kotlinx.serialization.json.JsonObject>("PUT", "/v1/me", obj("fcmToken" to token)) }
}
