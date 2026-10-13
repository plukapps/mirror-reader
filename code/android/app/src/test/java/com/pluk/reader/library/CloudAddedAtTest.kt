package com.pluk.reader.library

import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.cloudAddedAt
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAddedAtTest {
    private fun remote(id: String, createdAt: Long? = null) = RemoteBook(id, "T$id", emptyList(), 1, createdAt)

    // LIB-021: un libro nuevo de la nube entra con la fecha en que entró a la cuenta, no cuando se vio
    @Test
    fun newBookTakesTheCloudDate() {
        val result = cloudAddedAt(listOf(remote("a", createdAt = 100)), known = emptyMap(), now = 5_000)
        assertEquals(mapOf("a" to 100L), result.inserts)
        assertEquals(emptyMap<String, Long>(), result.updates)
    }

    // LIB-021: los de la nube se intercalan con los locales según su fecha, no quedan todos arriba
    @Test
    fun cloudBooksInterleaveWithLocalOnes() {
        val known = mapOf("local" to 200L)
        val result = cloudAddedAt(listOf(remote("viejo", 100), remote("nuevo", 300)), known, now = 5_000)
        val order = (known + result.inserts).entries.sortedByDescending { it.value }.map { it.key }
        assertEquals(listOf("nuevo", "local", "viejo"), order)
    }

    // LIB-021: un libro ya guardado con otra fecha (bajado antes o importado aquí) toma la de la nube
    @Test
    fun knownBookWithAnotherDateIsCorrected() {
        val result = cloudAddedAt(listOf(remote("a", 100), remote("b", 200)), known = mapOf("a" to 4_000L, "b" to 200L), now = 5_000)
        assertEquals(emptyMap<String, Long>(), result.inserts)
        assertEquals(mapOf("a" to 100L), result.updates)
    }

    // LIB-021: sin fecha en la nube se conserva lo que hay, y un libro nuevo usa la hora en que se vio
    @Test
    fun withoutCloudDateKeepsWhatThereIs() {
        val result = cloudAddedAt(listOf(remote("a"), remote("b"), remote("c")), known = mapOf("c" to 10L), now = 5_000)
        // El primero de la lista queda como el más reciente, como antes.
        assertEquals(mapOf("a" to 5_000L, "b" to 4_999L), result.inserts)
        assertEquals(emptyMap<String, Long>(), result.updates)
    }
}
