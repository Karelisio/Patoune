package com.karelisio.patoune.sync

import com.karelisio.patoune.data.Settings
import com.karelisio.patoune.domain.Crypto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec

/** Session de foyer : identifiants, jeton et trousseau de clés (une clé par epoch). */
data class Session(
    val server: String,
    val householdId: String,
    val memberId: String,
    val token: String,
    val epoch: Int,
    val keyring: Map<Int, ByteArray>,
    val isCreator: Boolean,
    val creatorPublicKey: String?,
) {
    val key: ByteArray get() = keyring.getValue(epoch)
}

/** Persistance de la session ; les secrets sont scellés par l'Android Keystore ([SecretBox]). */
class SessionStore(private val settings: Settings, private val box: SecretBox) {

    suspend fun load(): Session? {
        val s = settings.current()
        if (!s.shared) return null
        val sec = settings.secrets()
        val token = sec.tokenEnc?.let { box.open(it).decodeToString() } ?: return null
        val ring = sec.keyringEnc?.let { decodeRing(box.open(it).decodeToString()) } ?: return null
        return Session(s.serverUrl, s.householdId!!, s.memberId!!, token, s.keyEpoch, ring, s.isCreator, sec.creatorPublic)
    }

    suspend fun save(server: String, householdId: String, memberId: String, token: String, epoch: Int, ring: Map<Int, ByteArray>, creator: Boolean, creatorPublic: String?) {
        settings.enterHousehold(householdId, memberId, server, creator, epoch, box.seal(token.toByteArray()), box.seal(encodeRing(ring).toByteArray()), creatorPublic)
    }

    suspend fun saveRing(epoch: Int, ring: Map<Int, ByteArray>) = settings.setKeyring(epoch, box.seal(encodeRing(ring).toByteArray()))

    /** Paire de clés ECDH de l'appareil (générée une fois). */
    suspend fun deviceKeys(): KeyPair {
        val sec = settings.secrets()
        if (sec.ecPrivateEnc != null && sec.ecPublic != null) {
            val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(box.open(sec.ecPrivateEnc)))
            return KeyPair(Crypto.publicKeyFromBytes(Crypto.unb64(sec.ecPublic)), priv)
        }
        val kp = Crypto.newKeyPair()
        settings.setDeviceKeys(box.seal(kp.private.encoded), Crypto.b64(kp.public.encoded))
        return kp
    }

    companion object {
        fun encodeRing(ring: Map<Int, ByteArray>): String = JsonObject(ring.entries.associate { it.key.toString() to JsonPrimitive(Crypto.b64(it.value)) }).toString()
        fun decodeRing(s: String): Map<Int, ByteArray> = Json.parseToJsonElement(s).jsonObject.entries.associate { it.key.toInt() to Crypto.unb64(it.value.jsonPrimitive.content) }
    }
}
