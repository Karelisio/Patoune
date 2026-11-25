package com.karelisio.patoune.domain

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Chiffrement de bout en bout : AES-256-GCM, nonce aléatoire de 12 octets préfixé au chiffré. */
object Crypto {
    private val random = SecureRandom()
    private const val NONCE = 12
    private const val TAG_BITS = 128

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)
    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    fun encrypt(key: ByteArray, plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val nonce = randomBytes(NONCE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        aad?.let(c::updateAAD)
        return nonce + c.doFinal(plain)
    }

    fun decrypt(key: ByteArray, blob: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, blob, 0, NONCE))
        aad?.let(c::updateAAD)
        return c.doFinal(blob, NONCE, blob.size - NONCE)
    }

    /** Dérivation d'une clé depuis un mot de passe (sauvegardes). */
    fun deriveFromPassword(password: CharArray, salt: ByteArray, iterations: Int = 120_000): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    // ---- ECDH P-256 pour transmettre une nouvelle clé de foyer aux membres restants ----

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    fun publicKeyFromBytes(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))

    fun sharedKey(private: PrivateKey, peer: PublicKey, info: String): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(private)
        ka.doPhase(peer, true)
        return hkdf(ka.generateSecret(), ByteArray(32), info.toByteArray(), 32)
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        var t = ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        var i = 1
        while (out.size() < len) {
            mac.update(t); mac.update(info); mac.update(i.toByte())
            t = mac.doFinal(); out.write(t); i++
        }
        return out.toByteArray().copyOf(len)
    }

    fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun unb64(s: String): ByteArray = Base64.getUrlDecoder().decode(s)

    private const val B32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Base32 sans caractères ambigus (pas de I, O, 0, 1) pour le code de secours. */
    fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0; var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff); bits += 8
            while (bits >= 5) { sb.append(B32[(buffer shr (bits - 5)) and 31]); bits -= 5 }
        }
        if (bits > 0) sb.append(B32[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    fun unbase32(s: String): ByteArray {
        val clean = s.uppercase().filter { it in B32 }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0; var bits = 0
        for (ch in clean) {
            buffer = (buffer shl 5) or B32.indexOf(ch); bits += 5
            if (bits >= 8) { out.write((buffer shr (bits - 8)) and 0xff); bits -= 8 }
        }
        return out.toByteArray()
    }
}
