package com.karelisio.patoune.sync

import com.karelisio.patoune.domain.Crypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Enveloppe chiffrée : [epoch sur 2 octets][AES-GCM(gzip(contenu))].
 * L'AAD lie le blob à son foyer et à son usage pour empêcher toute substitution.
 */
object Envelope {
    fun seal(key: ByteArray, epoch: Int, plain: ByteArray, aad: String, compress: Boolean = true): ByteArray {
        val body = if (compress) gzip(plain) else plain
        return byteArrayOf((epoch shr 8).toByte(), epoch.toByte()) + Crypto.encrypt(key, body, aad.toByteArray())
    }

    fun epochOf(blob: ByteArray): Int = ((blob[0].toInt() and 0xff) shl 8) or (blob[1].toInt() and 0xff)

    fun open(ring: Map<Int, ByteArray>, blob: ByteArray, aad: String, compressed: Boolean = true): ByteArray? {
        val key = ring[epochOf(blob)] ?: return null
        val plain = runCatching { Crypto.decrypt(key, blob.copyOfRange(2, blob.size), aad.toByteArray()) }.getOrNull() ?: return null
        return if (compressed) gunzip(plain) else plain
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
