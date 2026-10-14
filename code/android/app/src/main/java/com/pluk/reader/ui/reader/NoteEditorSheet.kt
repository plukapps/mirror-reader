package com.pluk.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.domain.model.ReaderFont

/**
 * ANN-008, ANN-009: editor de una nota. Muestra el texto citado y un campo para la nota. Guardar con el campo
 * vacío no hace nada. [onDelete] es null para una nota nueva; si no, ofrece borrar con confirmación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
    editor: NoteEditor,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var text by rememberSaveable(editor.noteId, editor.locatorJson) { mutableStateOf(editor.text) }
    var confirmDelete by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = SheetBackground,
        contentColor = SheetInk,
        scrimColor = Color.Black.copy(alpha = 0.2f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SheetLine))
        },
        modifier = Modifier.testTag("note-editor-sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(SheetAccent))
                Spacer(Modifier.width(12.dp))
                Text(
                    editor.quote,
                    color = SheetMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = ReaderFont.SERIF.labelFamily(), fontStyle = FontStyle.Italic, fontSize = 15.sp),
                    modifier = Modifier.testTag("note-quote"),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = SheetInk, fontSize = 16.sp),
                cursorBrush = SolidColor(SheetAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .border(1.5.dp, SheetLine, RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .focusRequester(focus)
                    .testTag("note-text"),
                decorationBox = { field ->
                    if (text.isEmpty()) Text(stringResource(R.string.note_placeholder), color = SheetMuted, fontSize = 16.sp)
                    field()
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("note-delete")) {
                        Text(stringResource(R.string.note_delete), color = SheetMuted)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("note-cancel")) {
                    Text(stringResource(R.string.note_cancel), color = SheetInk)
                }
                val canSave = text.isNotBlank()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (canSave) SheetAccent else SheetLine)
                        .clickable(enabled = canSave, role = Role.Button) { onSave(text) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .testTag("note-save"),
                ) {
                    Text(stringResource(R.string.note_save), color = if (canSave) SheetBackground else SheetMuted, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    // Una nota nueva arranca con el teclado abierto.
    LaunchedEffect(editor.noteId) { if (editor.noteId == null) runCatching { focus.requestFocus() } }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }, modifier = Modifier.testTag("note-delete-confirm")) {
                    Text(stringResource(R.string.note_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.note_cancel)) }
            },
        )
    }
}
