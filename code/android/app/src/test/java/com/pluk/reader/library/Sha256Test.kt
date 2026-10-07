package com.pluk.reader.library

import com.pluk.reader.data.library.copyWithSha256
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Sha256Test {
    private fun hash(bytes: ByteArray) = copyWithSha256(ByteArrayInputStream(bytes), ByteArrayOutputStream())

    // LIB-003
    @Test
    fun matchesKnownDigest() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("abc".toByteArray()))
    }

    // LIB-003: mismo contenido, mismo identificador
    @Test
    fun sameContentGivesSameHash() {
        assertEquals(hash("libro".toByteArray()), hash("libro".toByteArray()))
        assertNotEquals(hash("libro".toByteArray()), hash("libro2".toByteArray()))
    }

    // LIB-003: la copia es fiel byte a byte, también más allá de un buffer
    @Test
    fun copiesContentUnchanged() {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        copyWithSha256(ByteArrayInputStream(data), out)
        assertArrayEquals(data, out.toByteArray())
    }
}
