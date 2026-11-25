package com.karelisio.patoune

import com.karelisio.patoune.domain.Crypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoTest {
    @Test fun aesGcmRoundTrip() {
        val key = Crypto.newKey()
        val plain = "Prise donnée à 8h02 par Camille".toByteArray()
        val blob = Crypto.encrypt(key, plain, "aad".toByteArray())
        assertFalse(String(blob).contains("Camille"))
        assertArrayEquals(plain, Crypto.decrypt(key, blob, "aad".toByteArray()))
    }

    @Test fun nonceIsRandom() {
        val key = Crypto.newKey()
        val a = Crypto.encrypt(key, byteArrayOf(1, 2, 3))
        val b = Crypto.encrypt(key, byteArrayOf(1, 2, 3))
        assertFalse(a.contentEquals(b))
    }

    @Test fun wrongKeyOrTamperingFails() {
        val blob = Crypto.encrypt(Crypto.newKey(), "secret".toByteArray())
        assertThrows(Exception::class.java) { Crypto.decrypt(Crypto.newKey(), blob) }
        val key = Crypto.newKey()
        val good = Crypto.encrypt(key, "secret".toByteArray())
        good[good.size - 1] = (good.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { Crypto.decrypt(key, good) }
        assertThrows(Exception::class.java) { Crypto.decrypt(key, Crypto.encrypt(key, "x".toByteArray(), "a".toByteArray()), "b".toByteArray()) }
    }

    @Test fun passwordDerivationIsDeterministic() {
        val salt = Crypto.randomBytes(16)
        val k1 = Crypto.deriveFromPassword("mot de passe".toCharArray(), salt, 1000)
        val k2 = Crypto.deriveFromPassword("mot de passe".toCharArray(), salt, 1000)
        assertArrayEquals(k1, k2)
        assertFalse(k1.contentEquals(Crypto.deriveFromPassword("autre".toCharArray(), salt, 1000)))
    }

    @Test fun ecdhWrapsHouseholdKeyForMember() {
        val creator = Crypto.newKeyPair()
        val member = Crypto.newKeyPair()
        val householdKey = Crypto.newKey()
        val wrapKey = Crypto.sharedKey(creator.private, Crypto.publicKeyFromBytes(member.public.encoded), "patoune-epoch-2")
        val wrapped = Crypto.encrypt(wrapKey, householdKey)
        val unwrapKey = Crypto.sharedKey(member.private, Crypto.publicKeyFromBytes(creator.public.encoded), "patoune-epoch-2")
        assertArrayEquals(householdKey, Crypto.decrypt(unwrapKey, wrapped))
    }

    @Test fun base32RoundTrip() {
        val bytes = Crypto.randomBytes(40)
        val code = Crypto.base32(bytes)
        assertFalse(code.contains('O') || code.contains('I') || code.contains('0') || code.contains('1'))
        assertArrayEquals(bytes, Crypto.unbase32(code.chunked(4).joinToString("-").lowercase()))
        assertEquals(64, code.length)
    }
}
