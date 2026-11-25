package com.karelisio.patoune.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.karelisio.patoune.domain.Crypto
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protège les secrets locaux (clé du foyer, jeton, clé privée ECDH). */
interface SecretBox {
    fun seal(plain: ByteArray): String
    fun open(sealed: String): ByteArray
}

/** Implémentation Android Keystore : la clé maîtresse ne quitte jamais le matériel sécurisé. */
class KeystoreBox : SecretBox {
    private val alias = "patoune_master"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun seal(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Crypto.b64(c.iv + c.doFinal(plain))
    }

    override fun open(sealed: String): ByteArray {
        val blob = Crypto.unb64(sealed)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }
}

/** Pour les tests JVM (pas de Keystore) : chiffrement avec une clé en mémoire. */
class MemoryBox(private val k: ByteArray = Crypto.newKey()) : SecretBox {
    override fun seal(plain: ByteArray): String = Crypto.b64(Crypto.encrypt(k, plain))
    override fun open(sealed: String): ByteArray = Crypto.decrypt(k, Crypto.unb64(sealed))
}
