package com.pluk.reader.ui.reader

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Acciones del menú de selección del lector (ANN-008). Diseño: "06 — Reader · Classic + highlight". */
enum class SelectionAction { Note, Copy, Share }

/** El usuario eligió [action] sobre el texto seleccionado. [finish] cierra el menú y quita la selección. */
class SelectionRequest(val action: SelectionAction, val finish: () -> Unit)

/**
 * Menú que reemplaza al del sistema al seleccionar texto en el libro. Readium lo recibe en la configuración del
 * navegador, que se arma en la fábrica del fragmento; por eso es un objeto único y la pantalla escucha [requests].
 * La pantalla lee la selección antes de llamar a `finish`, porque cerrar el menú la quita.
 */
object SelectionMenu : ActionMode.Callback {
    private val _requests = MutableSharedFlow<SelectionRequest>(extraBufferCapacity = 1)
    val requests: SharedFlow<SelectionRequest> = _requests.asSharedFlow()

    /** Textos del menú; los pone la pantalla con los recursos del idioma actual. */
    @Volatile
    var labels: Map<SelectionAction, String> = mapOf(
        SelectionAction.Note to "Nota",
        SelectionAction.Copy to "Copiar",
        SelectionAction.Share to "Compartir",
    )

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        SelectionAction.entries.forEachIndexed { index, action ->
            menu.add(Menu.NONE, index, index, labels[action]).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val action = SelectionAction.entries.getOrNull(item.itemId) ?: return false
        // Sin nadie escuchando (no debería pasar), el menú se cierra igual.
        if (!_requests.tryEmit(SelectionRequest(action) { mode.finish() })) mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}
