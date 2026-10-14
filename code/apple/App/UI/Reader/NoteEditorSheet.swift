import ReaderDomain
import SwiftUI

/// ANN-008, ANN-009: editor de una nota, con el estilo del panel de ajustes, como `NoteEditorSheet` de Android. Muestra
/// el texto citado y un campo para la nota. Guardar con el campo vacío no hace nada. Una nota existente ofrece borrar,
/// con confirmación.
struct NoteEditorSheet: View {
    let editor: ReaderViewModel.NoteEditor
    let onSave: (String) -> Void
    let onCancel: () -> Void
    let onDelete: () -> Void
    @State private var text: String
    @State private var confirmDelete = false
    @FocusState private var focused: Bool

    init(editor: ReaderViewModel.NoteEditor, onSave: @escaping (String) -> Void, onCancel: @escaping () -> Void, onDelete: @escaping () -> Void) {
        self.editor = editor
        self.onSave = onSave
        self.onCancel = onCancel
        self.onDelete = onDelete
        _text = State(initialValue: editor.text)
    }

    private var canSave: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule()
                .fill(SheetColors.border)
                .frame(width: 36, height: 4)
                .frame(maxWidth: .infinity)
                .padding(.top, 10)
                .accessibilityHidden(true)

            HStack(alignment: .top, spacing: 12) {
                Rectangle().fill(SheetColors.accent).frame(width: 3)
                Text(editor.quote)
                    .font(.custom("Newsreader", size: 15).italic())
                    .foregroundStyle(SheetColors.label)
                    .lineLimit(4)
                    .accessibilityIdentifier("note-quote")
            }
            .fixedSize(horizontal: false, vertical: true)

            ZStack(alignment: .topLeading) {
                if text.isEmpty {
                    Text("Escribí tu nota")
                        .font(.app(16))
                        .foregroundStyle(SheetColors.muted)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 8)
                }
                TextEditor(text: $text)
                    .font(.app(16))
                    .foregroundStyle(SheetColors.ink)
                    .scrollContentBackground(.hidden)
                    .focused($focused)
                    .accessibilityIdentifier("note-text")
            }
            .frame(minHeight: 96)
            .padding(10)
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(SheetColors.border, lineWidth: 1.5))

            HStack(spacing: 8) {
                if editor.noteId != nil {
                    Button("Borrar") { confirmDelete = true }
                        .font(.app(15, .medium))
                        .foregroundStyle(SheetColors.label)
                        .accessibilityIdentifier("note-delete")
                }
                Spacer()
                Button("Cancelar", action: onCancel)
                    .font(.app(15, .medium))
                    .foregroundStyle(SheetColors.ink)
                    .padding(.horizontal, 12)
                Button { onSave(text) } label: {
                    Text("Guardar")
                        .font(.app(15, .semibold))
                        .foregroundStyle(canSave ? SheetColors.background : SheetColors.muted)
                        .padding(.horizontal, 20)
                        .frame(height: 40)
                        .background(Capsule().fill(canSave ? SheetColors.accent : SheetColors.border))
                }
                .disabled(!canSave)
                .accessibilityIdentifier("note-save")
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
        // Una nota nueva arranca con el teclado abierto.
        .onAppear { if editor.noteId == nil { focused = true } }
        .alert("¿Borrar la nota?", isPresented: $confirmDelete) {
            Button("Borrar", role: .destructive, action: onDelete)
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Se borra la nota y su subrayado.")
        }
    }
}
