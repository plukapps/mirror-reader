package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.repository.CloudBooksRepository
import javax.inject.Inject

/**
 * Trae los metadatos de los libros que hay en la nube y agrega a la biblioteca los que faltan, como
 * "solo en la nube" (LIB-007). No baja ningún archivo. Sin conexión no cambia nada (ADR 0002).
 */
class SyncRemoteBooksUseCase @Inject constructor(
    private val remote: RemoteLibrary,
    private val cloud: CloudBooksRepository,
) {
    suspend operator fun invoke(): Result<Unit> = remote.listBooks().map { cloud.addCloudOnly(it) }
}
