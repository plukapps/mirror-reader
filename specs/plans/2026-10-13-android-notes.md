# Plan — Notas sobre texto y detalle del libro (Android)

**Objetivo:** tomar una nota sobre un texto seleccionado en el lector, verla subrayada, verla en el detalle del libro y, al tocarla, abrir el libro en ese lugar.

**Specs que implementa:** ANN-003, ANN-004, ANN-005 (solo notas), ANN-006, ANN-008 a ANN-011 (`specs/product/03-annotations.md`), LIB-022 (`specs/product/01-library.md`).
**Rama:** `claude/notes-functionality-f6f30e` (worktree), desde `develop`, PR contra `develop`. **Tarjetas:** K-260 a K-266. Parte de K-009.
**Aprobado por el usuario (2026-10-14):** el plan tal cual, con "Ver detalle" en el menú de acciones.
**Pedido del usuario (2026-10-13):** nota sobre texto seleccionado; Android primero; solo local; las notas se ven en un detalle del libro (portada, título, autor, notas), como el diseño "04 — Book detail".

## Fuera de esta rebanada

- Marcadores (ANN-001), subrayado sin nota y los 4 colores (ANN-002): un solo color, el amarillo del diseño.
- Sincronización (ANN-007): el modelo guarda lo necesario (id estable, `updatedAt`), pero no se sube. Plan aparte con Firestore y reglas.
- iOS y Mac: rebanada aparte con el mismo spec.
- Panel de notas dentro del lector (T04, tablet) y la entrada "Notas" de la biblioteca.
- Copiar y compartir del menú de selección: se dejan las acciones del sistema.

## Decisiones

- **Modelo:** `Note(id: String (UUID), bookId, locatorJson, quote, text, progression: Double, createdAt, updatedAt)` en `domain/model`. El locator viaja serializado como la posición de lectura (el dominio no lo interpreta). `quote` es el texto seleccionado (ANN-006) y `progression` (`totalProgression` del locator) sirve para ordenar y para el "N %" de la lista.
- **Room:** tabla `notes` (versión 8, `MIGRATION_7_8`, esquema 8 exportado), índice por `bookId`. `NoteDao` con `observeByBook(bookId): Flow<List<NoteEntity>>` ordenado por `progression`, `get(id)`, `upsert`, `delete(id)`, `deleteByBook(bookId)`. `LocalDataWiper` ya limpia todas las tablas.
- **Repositorio:** `NoteRepository` en `domain/repository` (observe, get, save, delete), `NoteRepositoryImpl` en `data`, binding en `RepositoryModule`, DAO en `StorageModule` y `TestStorageModule`.
- **Menú de selección (ANN-008):** `selectionActionModeCallback` en `EpubNavigatorFragment.Configuration` (API verificada en las fuentes de Readium 3.4.0). El callback agrega "Nota" al menú del sistema; al tocarla pide `navigator.currentSelection()` (`Selection.locator` trae `text.highlight`) y abre el editor. El callback vive en `NavigatorFragmentHost` y avisa a la pantalla con un flujo, porque la configuración se arma en la fábrica del fragmento.
- **Subrayados (ANN-008, ANN-009):** `navigator.applyDecorations(notas, "notes")` con `Decoration.Style.Highlight(tint = amarillo del diseño)`, cada vez que cambia la lista. `addDecorationListener("notes")` abre la hoja de la nota tocada.
- **Editor:** bottom sheet con el texto citado, un campo de texto y Guardar / Cancelar (y Borrar si la nota existe, con confirmación). Guardar con el campo vacío no se permite. Estilo del panel de ajustes del lector.
- **Abrir en una nota (ANN-010):** la ruta del lector gana un argumento opcional `noteId` (`reader/{bookId}?noteId={noteId}`). `ReaderViewModel` lo lee del `SavedStateHandle`, busca la nota y usa su locator en `navigatorHost.install` en lugar de la última posición, sin pasar por el aviso de "continuar desde otro dispositivo". Se pasa el id y no el JSON para no meter JSON en la ruta.
- **Detalle (LIB-022):** ruta `book/{bookId}`, `BookDetailViewModel` con `LibraryRepository.books` filtrado por id más `NoteRepository.observe(bookId)`. Sin barra inferior, como Perfil → Cambiar contraseña. Muestra lo que la app conoce: portada (`BookCover`), título, autor, "N notas", lista y "Continuar leyendo · N %". Si el libro desaparece (quitado en otro dispositivo), vuelve atrás.
- **Entrada al detalle:** `BookAction.Details` ("Ver detalle") en el menú de acciones (LIB-016), en Inicio, Biblioteca y Búsqueda. Tocar la portada sigue abriendo el libro (LIB-017). Si el usuario prefiere que tocar abra el detalle, se cambia LIB-014/LIB-017 en otro paso.
- **Quitar libro (ANN-011):** `BookEditorImpl.remove` borra las notas del libro.

## Tareas

### K-260 — Spec y plan
- ANN-008 a ANN-011, LIB-022 y escenarios. Este plan. Tarjetas en `KANBAN.md`.

### K-261 — Modelo y persistencia
- `Note`, `NoteRepository`, `NoteEntity`, `NoteDao`, `MIGRATION_7_8`, esquema 8, DI en los dos módulos.
- Tests: `MigrationTest` 7→8 y test de DAO/repositorio en emulador (ANN-006, orden por lugar, borrar por libro); compilados, se corren en emulador con aviso.

### K-262 — Crear una nota desde la selección
- Callback de selección, flujo hacia la pantalla, editor (bottom sheet) y `ReaderViewModel.saveNote(locatorJson, quote, text, progression)`.
- Tests JVM en `ReaderViewModelTest` con `FakeNotes` (ANN-008: guardar, cancelar, texto vacío).

### K-263 — Subrayados y ver / editar / borrar en el lector
- `notes` en `ReaderUiState.Ready`, decoraciones, listener, hoja de la nota con editar y borrar.
- Tests JVM (ANN-009: editar conserva id y `createdAt`, borrar). Prueba a mano en el teléfono: el subrayado se ve y se toca.

### K-264 — Abrir el lector en una nota
- Argumento `noteId` en la ruta; `ReaderViewModel` abre en el locator de la nota.
- Tests JVM (ANN-010: abre en la nota y no en la última posición; nota inexistente abre en la última posición).

### K-265 — Detalle del libro
- `BookDetailScreen`, `BookDetailViewModel`, ruta, `BookAction.Details` en el menú, borrado de notas al quitar el libro (ANN-011).
- Tests JVM de `BookDetailViewModel` (LIB-022, ANN-010), `BookActionsViewModelTest` (menú con "Ver detalle"); test de pantalla en emulador compilado.

### K-266 — Cierre
- Notas en `specs/platforms/android.md`, resultado en este plan, `./gradlew :app:testDebugUnitTest` en verde.
- Verificación a mano en el teléfono (el usuario): crear una nota, verla subrayada, editarla, verla en el detalle, tocarla y llegar al lugar, borrarla, quitar el libro.

## Riesgos

- El menú de selección del WebView varía según el fabricante; se prueba en el teléfono del usuario (Samsung).
- Un locator de selección puede dejar de resolver si cambia el archivo; la nota se sigue viendo en el detalle con el texto citado (escenario de 03-annotations).
