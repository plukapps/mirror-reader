package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import javax.inject.Inject

/** Importa varios EPUB en orden; un fallo no detiene a los demás (LIB-001). */
class ImportBooksUseCase @Inject constructor(private val library: LibraryRepository) {
    suspend operator fun invoke(uris: List<String>): List<ImportOutcome> = uris.map { library.import(it) }
}
