package com.pluk.reader.reader

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalReadiumApi::class)
data class ReaderSession(
    val publication: Publication,
    val factory: EpubNavigatorFactory,
    val bookId: String,
    val initialLocator: Locator? = null,
)

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val session: ReaderSession) : ReaderState
    data class Failed(val message: String) : ReaderState
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val loader = PublicationLoader(app)
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    val session: ReaderSession? get() = (_state.value as? ReaderState.Ready)?.session
    private var started = false

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    fun toggleScroll() = update { it.toggleScroll() }
    fun nextTheme() = update { it.nextTheme() }
    fun biggerFont() = update { it.biggerFont() }
    fun smallerFont() = update { it.smallerFont() }
    fun toggleControls() { _controlsVisible.value = !_controlsVisible.value }

    private fun update(transform: (ReaderSettings) -> ReaderSettings) {
        _settings.value = transform(_settings.value)
    }

    fun open(uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            loader.load(uri).fold(
                onSuccess = { publication ->
                    val bookId = publication.metadata.identifier ?: uri.toString()
                    _state.value = ReaderState.Ready(
                        ReaderSession(publication, EpubNavigatorFactory(publication), bookId),
                    )
                },
                onFailure = { _state.value = ReaderState.Failed(it.message ?: "No se pudo abrir el libro.") },
            )
        }
    }
}
