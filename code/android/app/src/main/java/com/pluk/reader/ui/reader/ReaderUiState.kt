package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderSettings

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Failed(val message: String) : ReaderUiState

    /**
     * SYN-003: otro dispositivo leyó más adelante y la diferencia es grande. Se pregunta si continuar desde allí.
     * [remotePercent] es el avance de esa lectura, o null si se desconoce.
     */
    data class ResumePrompt(val deviceName: String, val remotePercent: Int?) : ReaderUiState

    data class Ready(
        val book: OpenedBook,
        val toc: List<TocEntry>,
        val settings: ReaderSettings,
        val progressPercent: Int?,
        /** RDR-010: posición actual en el libro (1 en adelante), o null si todavía no hay dato. */
        val pageNumber: Int?,
        val controlsVisible: Boolean,
        /** RDR-013: título del capítulo actual, vacío si no se conoce. */
        val chapterTitle: String = "",
        /** RDR-014: el panel de ajustes está abierto. */
        val settingsOpen: Boolean = false,
        /** SYN-013: otro dispositivo leyó más adelante este libro. Null si no hay nada que ofrecer. */
        val continueFrom: ContinueFrom? = null,
    ) : ReaderUiState
}

/** Eventos de un solo uso hacia la pantalla. */
sealed interface ReaderEvent {
    data class OpenExternalLink(val url: String) : ReaderEvent

    /** RDR-012: el usuario pasó del cuerpo del libro a sus páginas finales. */
    data object BodyEnded : ReaderEvent
}

/** SYN-013: lectura más adelantada en otro dispositivo, para el aviso "Seguir desde...". */
data class ContinueFrom(val deviceName: String, val percent: Int?)
