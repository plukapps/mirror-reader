package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderSettings

/**
 * Prepara el navegador de EPUB (un Fragment de Readium) para el libro abierto.
 *
 * El `ReaderViewModel` solo conoce esta interfaz, así que se prueba en JVM con un doble.
 */
interface NavigatorHost {
    /** Debe llamarse antes de que la pantalla muestre el navegador. */
    fun install(book: OpenedBook, settings: ReaderSettings, onExternalLink: (String) -> Unit)

    /** RDR-016: la última posición conocida. Si Android recrea el navegador (girar), vuelve ahí. */
    fun onLocatorChanged(locatorJson: String)

    fun clear()
}
