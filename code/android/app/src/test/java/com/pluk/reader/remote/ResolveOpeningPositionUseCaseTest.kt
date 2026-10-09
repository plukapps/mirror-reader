package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.usecase.OpeningPosition
import com.pluk.reader.domain.usecase.ResolveOpeningPositionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SYN-003, SYN-012: al abrir un libro se usa la lectura más reciente; se pregunta solo si el salto es grande. */
class ResolveOpeningPositionUseCaseTest {
    private val backend = FakePositionBackend()
    private val saved = mutableListOf<Triple<String, String, Double?>>()

    private val positions = object : PositionRepository {
        override suspend fun get(bookId: String): String? = null
        override suspend fun save(bookId: String, locatorJson: String, totalProgression: Double?) {
            saved += Triple(bookId, locatorJson, totalProgression)
        }
    }

    private fun useCase(session: AccountUser? = AccountUser("u1", null)) =
        ResolveOpeningPositionUseCase(backend, backend, positions, FakeSession(session))

    private fun remote(readAt: Long, progress: Double?, locator: String = "remota") =
        backend.remotePosition("a", readAt, locator = locator).let { it.copy(position = it.position.copy(progress = progress)) }

    private fun local(readAt: Long, progress: Double?, synced: Boolean = true) {
        backend.local["a"] = com.pluk.reader.domain.model.LocalPosition(
            ReadingPosition("a", "local", progress, readAt),
            synced,
        )
    }

    @Test
    fun withoutASessionTheLocalPositionIsUsed() = runTest {
        backend.fetchResult = Result.success(remote(50, 0.9))
        assertEquals(OpeningPosition.Local, useCase(session = null)("a"))
    }

    // Sin conexión leer no se bloquea
    @Test
    fun offlineTheLocalPositionIsUsed() = runTest {
        local(10, 0.1)
        backend.fetchResult = Result.failure(RemoteUnavailableException("sin red"))
        assertEquals(OpeningPosition.Local, useCase()("a"))
        assertEquals("local", backend.local.getValue("a").position.locatorJson)
    }

    @Test
    fun nothingInTheCloudKeepsTheLocalPosition() = runTest {
        local(10, 0.1)
        backend.fetchResult = Result.success(null)
        assertEquals(OpeningPosition.Local, useCase()("a"))
    }

    // Una nube lenta no demora la apertura más que el tiempo máximo
    @Test
    fun aSlowCloudIsAbandonedAfterTheTimeout() = runTest {
        local(10, 0.1)
        backend.fetchDelayMs = 60_000
        backend.fetchResult = Result.success(remote(50, 0.9))

        val result = useCase()("a")

        assertEquals(OpeningPosition.Local, result)
        assertEquals(ResolveOpeningPositionUseCase.FETCH_TIMEOUT_MS, testScheduler.currentTime)
    }

    // Dispositivo nuevo: no hay posición local, se usa la de la nube sin preguntar
    @Test
    fun noLocalPositionTakesTheCloudOneWithoutAsking() = runTest {
        backend.fetchResult = Result.success(remote(50, 0.6))
        assertEquals(OpeningPosition.Updated, useCase()("a"))
        assertEquals("remota", backend.local.getValue("a").position.locatorJson)
    }

    // SYN-003: hasta el 2 % de diferencia salta sola
    @Test
    fun aSmallDifferenceJumpsWithoutAsking() = runTest {
        local(10, 0.50)
        backend.fetchResult = Result.success(remote(50, 0.52))
        assertEquals(OpeningPosition.Updated, useCase()("a"))
        assertEquals("remota", backend.local.getValue("a").position.locatorJson)
    }

    // SYN-003: más del 2 % pregunta y no cambia nada hasta que el usuario elija
    @Test
    fun aBigDifferenceAsksAndChangesNothingYet() = runTest {
        local(10, 0.50)
        backend.fetchResult = Result.success(remote(50, 0.53))

        val result = useCase()("a")

        assertTrue(result is OpeningPosition.Confirm)
        result as OpeningPosition.Confirm
        assertEquals("remota", result.remote.position.locatorJson)
        assertEquals("local", result.local.locatorJson)
        assertEquals("local", backend.local.getValue("a").position.locatorJson)
    }

    // Sin progresión no se puede medir: se pregunta
    @Test
    fun anUnknownProgressionAsks() = runTest {
        local(10, null)
        backend.fetchResult = Result.success(remote(50, 0.5))
        assertTrue(useCase()("a") is OpeningPosition.Confirm)
    }

    // SYN-003, SYN-010: una lectura local más nueva no se pisa
    @Test
    fun aNewerLocalPositionIsKept() = runTest {
        local(90, 0.1, synced = false)
        backend.fetchResult = Result.success(remote(50, 0.9))
        assertEquals(OpeningPosition.Local, useCase()("a"))
        assertEquals("local", backend.local.getValue("a").position.locatorJson)
    }

    // El eco de lo que este dispositivo ya envió
    @Test
    fun theSameReadingMarksTheLocalAsSynced() = runTest {
        local(50, 0.5, synced = false)
        backend.fetchResult = Result.success(remote(50, 0.5))
        assertEquals(OpeningPosition.Local, useCase()("a"))
        assertTrue(backend.local.getValue("a").isSynced)
    }

    // El usuario elige continuar desde el otro dispositivo
    @Test
    fun acceptingAppliesTheCloudPosition() = runTest {
        local(10, 0.1)
        useCase().accept(remote(50, 0.9))
        assertEquals("remota", backend.local.getValue("a").position.locatorJson)
    }

    // El usuario elige quedarse: su posición se guarda de nuevo para ser la más reciente
    @Test
    fun decliningSavesTheLocalPositionAgainSoItBecomesTheNewest() = runTest {
        useCase().decline(ReadingPosition("a", "local", 0.1, 10))
        assertEquals(listOf(Triple("a", "local", 0.1)), saved)
    }
}
