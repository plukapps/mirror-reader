package com.pluk.reader.search

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.store.Acquisition
import com.pluk.reader.domain.store.CatalogResult
import com.pluk.reader.domain.store.DeliveryOutcome
import com.pluk.reader.domain.store.SearchFilter
import com.pluk.reader.domain.store.StoreAcquisitions
import com.pluk.reader.domain.store.StoreBook
import com.pluk.reader.domain.store.StoreCatalog
import com.pluk.reader.domain.store.StoreDelivery
import com.pluk.reader.ui.search.SearchViewModel
import com.pluk.reader.ui.search.StoreItemState
import com.pluk.reader.ui.search.StoreStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary(initial: List<LibraryBook>) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Imported("x", "X")
    }

    private class FakeCatalog : StoreCatalog {
        val queries = mutableListOf<Pair<String, Boolean>>()
        var result: CatalogResult = CatalogResult.Found(emptyList())
        override suspend fun search(query: String, byAuthor: Boolean): CatalogResult {
            queries += query to byAuthor
            return result
        }
    }

    private class FakeDelivery : StoreDelivery {
        val delivered = mutableListOf<List<String>>()
        var next: CompletableDeferred<DeliveryOutcome> = CompletableDeferred()
        override suspend fun deliver(sources: List<String>): DeliveryOutcome {
            delivered += sources
            return next.await()
        }
    }

    private class FakeAcquisitions : StoreAcquisitions {
        override val acquired = MutableStateFlow<Map<String, String>>(emptyMap())
        override suspend fun record(workKey: String, bookId: String) {
            acquired.value = acquired.value + (workKey to bookId)
        }
    }

    private val library = FakeLibrary(
        listOf(
            LibraryBook("let", "Cartas a Lucilio", "Séneca", null, 42),
            LibraryBook("abo", "Vida de Séneca", "Pierre Grimal", null, null),
        ),
    )
    private val catalog = FakeCatalog()
    private val delivery = FakeDelivery()
    private val acquisitions = FakeAcquisitions()

    private val freeBook = StoreBook("/works/F", "Sobre la brevedad de la vida", listOf("Séneca"), null, Acquisition.Free(listOf("f.epub")))
    private val paidBook = StoreBook("/works/P", "De la ira", listOf("Séneca"), null, Acquisition.Paid(listOf("p.epub"), 499))

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        SearchViewModel(library, catalog, delivery, acquisitions, saved)

    /** Mantiene suscrito el estado, como la pantalla. */
    private fun TestScope.collect(vm: SearchViewModel) = vm.uiState.launchIn(backgroundScope)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // LIB-006: sin consulta no hay resultados
    @Test
    fun startsWithoutQuery() = runTest(dispatcher) {
        val vm = viewModel()
        val state = vm.uiState.first { !it.loading }
        assertFalse(state.hasQuery)
        assertTrue(state.results.isEmpty())
        assertEquals(StoreStatus.Idle, state.storeStatus)
    }

    // LIB-013: los resultados de la biblioteca aparecen mientras escribo
    @Test
    fun typingShowsResults() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onQueryChange("seneca")
        val state = vm.uiState.first { it.query == "seneca" && !it.loading }
        assertTrue(state.hasQuery)
        assertEquals(listOf("abo", "let"), state.results.map { it.id })
    }

    // LIB-015, STO-006
    @Test
    fun authorsFilterFiltersResults() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onQueryChange("seneca")
        vm.onFilterChange(SearchFilter.Authors)
        val state = vm.uiState.first { it.filter == SearchFilter.Authors && !it.loading }
        assertEquals(listOf("let"), state.results.map { it.id })
    }

    // LIB-014: el progreso del resultado sigue a la biblioteca (al volver del lector)
    @Test
    fun followsLibraryChanges() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onQueryChange("cartas")
        vm.uiState.first { it.results.isNotEmpty() }
        library.state.value = listOf(LibraryBook("let", "Cartas a Lucilio", "Séneca", null, 80))
        val state = vm.uiState.first { it.results.firstOrNull()?.progressPercent == 80 }
        assertEquals(listOf("let"), state.results.map { it.id })
    }

    // La consulta y el filtro sobreviven a la recreación (rotación)
    @Test
    fun restoresQueryAndFilter() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            onQueryChange("seneca")
            onFilterChange(SearchFilter.Authors)
        }
        val state = viewModel(saved).uiState.first { !it.loading }
        assertEquals("seneca", state.query)
        assertEquals(SearchFilter.Authors, state.filter)
        assertEquals(listOf("let"), state.results.map { it.id })
    }

    @Test
    fun clearEmptiesTheQuery() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onQueryChange("seneca")
        vm.onClear()
        val state = vm.uiState.first { !it.loading && it.query.isEmpty() }
        assertTrue(state.results.isEmpty())
    }

    // STO-001: el catálogo se consulta tras una pausa, una sola vez por consulta
    @Test
    fun catalogWaitsForAPause() = runTest(dispatcher) {
        catalog.result = CatalogResult.Found(listOf(freeBook, paidBook))
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("sen")
        advanceTimeBy(200)
        vm.onQueryChange("seneca")
        advanceTimeBy(200)
        runCurrent()
        assertTrue(catalog.queries.isEmpty())
        assertEquals(StoreStatus.Loading, vm.uiState.value.storeStatus)
        advanceUntilIdle()
        assertEquals(listOf("seneca" to false), catalog.queries)
        val state = vm.uiState.value
        assertEquals(StoreStatus.Ready, state.storeStatus)
        assertEquals(listOf(freeBook, paidBook), state.storeResults.map { it.book })
    }

    // STO-001: con un solo carácter no se consulta el catálogo
    @Test
    fun shortQueryDoesNotHitTheCatalog() = runTest(dispatcher) {
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("s")
        advanceUntilIdle()
        assertTrue(catalog.queries.isEmpty())
        assertEquals(StoreStatus.Idle, vm.uiState.value.storeStatus)
    }

    // STO-006: "Autores" busca en el catálogo solo por autor; "Gratis" deja solo lo gratis y oculta la biblioteca
    @Test
    fun filtersApplyToTheCatalog() = runTest(dispatcher) {
        catalog.result = CatalogResult.Found(listOf(freeBook, paidBook))
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("seneca")
        vm.onFilterChange(SearchFilter.Authors)
        advanceUntilIdle()
        assertEquals(listOf("seneca" to true), catalog.queries)
        vm.onFilterChange(SearchFilter.Free)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.results.isEmpty())
        assertEquals(listOf(freeBook), state.storeResults.map { it.book })
    }

    // STO-007: sin conexión, la biblioteca sigue y la tienda avisa
    @Test
    fun unavailableCatalogKeepsLibraryResults() = runTest(dispatcher) {
        catalog.result = CatalogResult.Unavailable
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("seneca")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(StoreStatus.Unavailable, state.storeStatus)
        assertEquals(listOf("abo", "let"), state.results.map { it.id })
    }

    // STO-003, STO-008: obtener descarga, registra la obra y el resultado pasa a "En tu biblioteca"
    @Test
    fun getDeliversTheBook() = runTest(dispatcher) {
        catalog.result = CatalogResult.Found(listOf(freeBook))
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("brevedad")
        advanceUntilIdle()
        vm.onGet(freeBook)
        runCurrent()
        assertEquals(StoreItemState.Downloading, vm.uiState.value.itemStates[freeBook.key])
        assertEquals(listOf(listOf("f.epub")), delivery.delivered)

        delivery.next.complete(DeliveryOutcome.Delivered("hash-f"))
        library.state.value = library.state.value + LibraryBook("hash-f", "On the Shortness of Life", "Seneca", null, null)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(mapOf(freeBook.key to "hash-f"), acquisitions.acquired.value)
        assertEquals("hash-f", state.storeResults.single().inLibrary?.id)
        assertNull(state.itemStates[freeBook.key])
    }

    // STO-003: si falla se avisa y se puede reintentar
    @Test
    fun failedDeliveryCanBeRetried() = runTest(dispatcher) {
        catalog.result = CatalogResult.Found(listOf(freeBook))
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("brevedad")
        advanceUntilIdle()
        vm.onGet(freeBook)
        delivery.next.complete(DeliveryOutcome.Failed("No se pudo descargar el libro."))
        advanceUntilIdle()
        assertEquals(StoreItemState.Failed("No se pudo descargar el libro."), vm.uiState.value.itemStates[freeBook.key])
        assertTrue(acquisitions.acquired.value.isEmpty())

        delivery.next = CompletableDeferred(DeliveryOutcome.Delivered("hash-f"))
        vm.onGet(freeBook)
        advanceUntilIdle()
        assertEquals(2, delivery.delivered.size)
        assertEquals(StoreItemState.Delivered("hash-f"), vm.uiState.value.itemStates[freeBook.key])
    }

    // STO-004: comprar pide confirmación; cancelar no descarga, confirmar entrega como obtener
    @Test
    fun purchaseIsConfirmedBeforeDelivery() = runTest(dispatcher) {
        catalog.result = CatalogResult.Found(listOf(paidBook))
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("ira")
        advanceUntilIdle()
        vm.onBuy(paidBook)
        advanceUntilIdle()
        assertEquals(paidBook, vm.uiState.value.pendingPurchase)
        vm.onDismissPurchase()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingPurchase)
        assertTrue(delivery.delivered.isEmpty())

        vm.onBuy(paidBook)
        vm.onConfirmPurchase()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingPurchase)
        assertEquals(listOf(listOf("p.epub")), delivery.delivered)
    }

    // STO-008: un libro obtenido no se repite en la parte de la biblioteca
    @Test
    fun acquiredBookIsNotRepeatedInLibrary() = runTest(dispatcher) {
        acquisitions.acquired.value = mapOf("/works/L" to "let")
        catalog.result = CatalogResult.Found(listOf(freeBook.copy(key = "/works/L", title = "Cartas a Lucilio")))
        val vm = viewModel()
        collect(vm)
        vm.onQueryChange("cartas")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.results.isEmpty())
        assertEquals("let", state.storeResults.single().inLibrary?.id)
    }

    // STO-006: con "En mi biblioteca" la tienda no participa, ni siquiera como "buscando"
    @Test
    fun inLibraryFilterIgnoresTheStore() = runTest(dispatcher) {
        catalog.result = CatalogResult.Found(listOf(freeBook))
        val vm = viewModel()
        collect(vm)
        vm.onFilterChange(SearchFilter.InLibrary)
        vm.onQueryChange("zzz")
        runCurrent()
        assertEquals(StoreStatus.Idle, vm.uiState.value.storeStatus)
        advanceUntilIdle()
        assertEquals(StoreStatus.Idle, vm.uiState.value.storeStatus)
        assertTrue(vm.uiState.value.storeResults.isEmpty())
    }
}
