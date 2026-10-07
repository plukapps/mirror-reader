package com.pluk.reader.library

import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportBooksUseCaseTest {
    private class FakeLibrary(val outcomes: Map<String, ImportOutcome>) : LibraryRepository {
        val requested = mutableListOf<String>()
        override val books: Flow<List<LibraryBook>> = emptyFlow()
        override suspend fun import(uri: String): ImportOutcome {
            requested += uri
            return outcomes.getValue(uri)
        }
    }

    // LIB-001: varios archivos, y un rechazo no detiene a los demás
    @Test
    fun importsEveryFileEvenIfOneIsRejected() = runTest {
        val library = FakeLibrary(
            mapOf(
                "a" to ImportOutcome.Imported("1", "A"),
                "b" to ImportOutcome.Rejected("dañado"),
                "c" to ImportOutcome.AlreadyInLibrary("3", "C"),
            ),
        )
        val results = ImportBooksUseCase(library)(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), library.requested)
        assertEquals(
            listOf(
                ImportOutcome.Imported("1", "A"),
                ImportOutcome.Rejected("dañado"),
                ImportOutcome.AlreadyInLibrary("3", "C"),
            ),
            results,
        )
    }
}
