package com.pluk.reader.domain.store

import kotlinx.coroutines.flow.Flow

/** Resultado de buscar en el catálogo. Nunca se lanza: sin conexión o con error, `Unavailable` (STO-007). */
sealed interface CatalogResult {
    data class Found(val books: List<StoreBook>) : CatalogResult

    data object Unavailable : CatalogResult
}

/** Catálogo de la tienda (STO-001). */
interface StoreCatalog {
    /** Busca [query] en título y autor, o solo en el autor con [byAuthor] (STO-006). */
    suspend fun search(query: String, byAuthor: Boolean): CatalogResult
}

/** Resultado de entregar un libro de la tienda a la biblioteca (STO-003). */
sealed interface DeliveryOutcome {
    /** En la biblioteca: recién importado o ya estaba (LIB-003, STO-008). */
    data class Delivered(val bookId: String) : DeliveryOutcome

    /** [message] está listo para mostrarse. */
    data class Failed(val message: String) : DeliveryOutcome
}

/** Descarga un EPUB y lo importa a la biblioteca, sin pasos del usuario (STO-003). */
interface StoreDelivery {
    /** Prueba las [sources] en orden hasta que una se importa. Nunca lanza. */
    suspend fun deliver(sources: List<String>): DeliveryOutcome
}

/** Libros obtenidos de la tienda: clave de la obra → id del libro en la biblioteca (STO-008). */
interface StoreAcquisitions {
    val acquired: Flow<Map<String, String>>

    suspend fun record(workKey: String, bookId: String)
}
