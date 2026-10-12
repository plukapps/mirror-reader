# Plan — Acciones sobre un libro con mantener presionado (Android e iOS)

**Objetivo:** que mantener presionada una portada abra el menú de la pantalla "05b — Library · Long press" de `design/Margin Ebook App.dc.html` con cuatro acciones: Leer libro, Marcar como terminado (o como no leído), Subir foto de portada y Quitar de la biblioteca.

**Specs que implementa:** LIB-016 a LIB-020 (`specs/product/01-library.md`, nuevos). Toca LIB-008, LIB-010, LIB-011 y LIB-012 (excepciones explícitas en los requisitos nuevos).
**Rama:** `feature/library-actions` (worktree `.claude/worktrees/library-actions`). **Tarjetas:** K-172 a K-176.
**Pedido del usuario (2026-10-12):** "primero siempre en local", Android e iOS. Decisiones tomadas con el usuario en esta sesión:
- Quitar: borra en este dispositivo y deja una marca local para que la sincronización no lo traiga de nuevo. La nube y los otros dispositivos, con K-154.
- Terminado: se guarda como posición (100 %) y viaja con la sincronización de posición que ya existe.
- En un libro terminado la opción es "Marcar como no leído".
- El menú se abre en la biblioteca, en Inicio y en la búsqueda.

## Diseño (medidas del HTML, pantalla 05b)

- Fondo: velo `#130000` al 55 % con desenfoque de 3 px sobre toda la pantalla, también sobre la barra inferior.
- Portada elegida: por encima del velo, escalada a 1,08, con borde blanco de 3 dp y sombra (0 18 30 −10, negro al 60 %).
- Tarjeta: 236 dp de ancho, centrada sobre la portada y 14 dp por encima. Si no entra, va debajo. Siempre queda dentro de la pantalla con 16 dp de margen. Fondo `#FDFDFD`, radio 20 dp, sombra (0 24 48 −16, negro al 55 %).
  - Cabecera: relleno 14/18/12 dp. Título 15 sp bold y "Autor · estado" 12 sp `Muted`. Línea `Line` de 1 dp debajo.
  - Filas: 50 dp de alto, relleno 18 dp a los lados, ícono de 22 dp y 12 dp de separación, texto 15 sp semibold.
  - "Leer libro" lleva fondo amarillo `#FBD256` y el ícono `menu_book` relleno.
  - "Marcar como terminado" usa `check_circle`. "Marcar como no leído" usa `remove_done`.
  - "Subir foto de portada" usa `add_photo_alternate` y lleva una línea debajo.
  - "Quitar de la biblioteca" usa `delete`, texto e ícono `#B3261E`.
- Textos: "Leer libro", "Marcar como terminado", "Marcar como no leído", "Subir foto de portada", "Quitar de la biblioteca". El estado de la cabecera es "Sin empezar", "42 %" o "Terminado". Sin autor, solo el estado.
- Confirmación de quitar (LIB-020): título "¿Quitar «Drácula»?", texto "Se borra de este dispositivo con su progreso. Podés volver a importarlo." y botones "Cancelar" y "Quitar" (rojo).

## Decisiones

### Dominio común a las dos plataformas

- **"No leído" como posición.** El estado sale de la posición:
  - Hoy es "nuevo" si no hay posición, "terminado" si el progreso llega al 100 % y "leyendo" en otro caso.
  - Se agrega una regla: una posición con el localizador `{"unread":true}` (`UNREAD_LOCATOR`, `unreadLocator`) y progreso menor a 100 % también es "nuevo". No se usa `"{}"`: el lector de iOS lo guarda cuando no puede serializar una posición real, y los tests lo usan como relleno.
  - "Marcar como no leído" guarda ese localizador con progreso 0. Como es una posición más nueva, viaja con la sincronización de posición (LIB-018) y los otros dispositivos aplican la misma regla.
  - Las reglas de Firestore exigen un `locatorJson` de 1 carácter o más, y este lo cumple.
  - Los dos lectores ya toleran un localizador sin `href`: `Locator.fromJSON` devuelve null y `try? Locator(jsonString:)` da nil, así que abren al principio.
  - Los avisos "¿Continuar desde…?" y "Seguir desde…" comparan progresos y no cambian.
- **"Marcar como terminado"** guarda progreso 1,0 con el localizador actual del libro, o el de "no leído" si nunca se abrió. Se usa el mismo `save` del lector, que marca la posición como pendiente y le pone la hora actual. Después se pide un envío (`requestFlush` en Android, `flush` en iOS) para no esperar a la próxima pasada.
- **Quitar con marca local.**
  - Se borran el registro del libro, la posición, el archivo EPUB y la portada.
  - El id queda en una tabla aparte de libros quitados (`removed_books`), con su fecha.
  - `addCloudOnly` ignora los ids de esa tabla, así la pasada de sincronización no lo vuelve a agregar ni a bajar. Los envíos pendientes (subida del libro, portada, posición) ya no lo encuentran porque el registro no existe.
  - Importar el mismo archivo borra la marca y lo agrega como libro nuevo, sin progreso.
  - Una tabla aparte en lugar de un campo `removedAt` evita filtrar el libro en cada consulta existente. La limpieza en la nube queda para K-154, que leerá esta tabla para mandar `deletedAt`.
  - Se descartó borrar sin marca: el libro volvería en la próxima pasada.
- **Portada propia, solo local.**
  - La foto se escala a 600 px de alto como máximo y se guarda en JPEG al 85 % sobre `covers/<id>.jpg`, el mismo archivo y el mismo proceso que la portada del EPUB.
  - Queda `hasCover = true`. `isCoverUploaded` no se toca: si el libro todavía no se subió, la portada nueva sube con él; si ya estaba subido, la nube conserva la anterior (excepción de LIB-019, la nube no sobrescribe portadas).
  - Para que las pantallas vean el cambio aunque la ruta sea la misma, se agrega `coverVersion` (la hora del cambio) al libro. La portada se vuelve a leer cuando cambia ruta o versión.
- **"Leer libro"** usa el mismo camino que tocar la portada.

### Android

- **Base de datos:** versión 6.
  - `MIGRATION_5_6` crea `removed_books(id TEXT PRIMARY KEY NOT NULL, removedAt INTEGER NOT NULL)` y agrega `coverVersion INTEGER NOT NULL DEFAULT 0` a `books`.
  - `MigrationTest` cubre 5→6. Es de emulador: se compila y no se corre sin preguntar.
- **Dominio:** caso de uso `BookActions` (`domain/usecase`) con `markFinished`, `markUnread`, `setCover` y `remove`. Usa `PositionRepository`, una interfaz nueva `BookEditor` (`domain/repository`: `replaceCover(bookId, imageUri)` y `remove(bookId)`) y `PositionFlushRequester` (lo implementa `PositionSync`). Es aparte de `LibraryRepository` para no tocar los seis `FakeLibrary` de los tests. `BookEditorImpl` en `data`, enlazado en `RepositoryModule`. Las consultas de `removed_books` viven en `BookDao`.
- **Estado de lectura:** se ajusta `LibraryRepositoryImpl.books` a la regla de "no leído". La lógica pura va en `domain` (`readingStatus`/mapeo) con test JVM.
- **UI:**
  - `BookActionsMenu` en `ui/library` es un `Popup` a pantalla completa con el velo, la portada elevada en la posición de la original y la tarjeta. La posición se calcula con una función pura con test JVM.
  - El desenfoque usa `Modifier.blur` sobre el contenido de la pantalla en Android 12 o superior. En versiones anteriores queda solo el velo.
  - Cada portada que abre un libro pasa a `combinedClickable(onClick, onLongClick)`, con vibración corta. Son `BookCell` (biblioteca), `ContinueCard` y `RowItem` (Inicio) y `ResultRow` (búsqueda).
  - Para TalkBack se agrega una acción personalizada "Más acciones" (HOM-007).
- **ViewModel:** `BookActionsViewModel` (Hilt), compartido por las tres pantallas. Tiene el libro elegido en un `StateFlow`, las acciones y el pedido de confirmación. Los mensajes van por `SharedFlow`: "Marcado como terminado", "Quitado de la biblioteca" o "No se pudo usar esa foto". Cada pantalla lo obtiene con `hiltViewModel()` y lo conecta a `BookActionsHost`, que dibuja el menú, el diálogo y el selector de fotos.
- **Selector de fotos:** `ActivityResultContracts.PickVisualMedia(ImageOnly)` (Photo Picker), sin permisos. Se verifica en la documentación oficial antes de usarlo.

### iOS (y Mac)

- **Datos:** modelo SwiftData nuevo `RemovedBookRecord(id, removedAt)` y campo `coverVersion: Int64 = 0` en `BookRecord`. Los valores por defecto permiten la migración automática (ADR 0012).
  - `LibraryStore` implementa un protocolo nuevo `BookActions` en `ReaderDomain` con `markFinished`, `markUnread`, `setCover(bookId:imageData:)` y `remove`.
  - `addCloudOnly` salta los quitados y `addImported` borra la marca.
  - La regla de "no leído" va en `ReaderDomain` (`readingStatus`/`LibraryStore.books()`).
- **UI:**
  - Se usa `.contextMenu(menuItems:preview:)` nativo en `BookCell`, `ContinueCard`, `BookRow` y `ResultRow`. En iPhone es mantener presionado, con la portada elevada y el fondo desenfocado del sistema, que es lo que dibuja el diseño. En la Mac es el clic secundario.
  - La cabecera (título y "Autor · estado") es un `Section` con encabezado.
  - "Quitar de la biblioteca" usa `role: .destructive`.
  - Se descartó copiar la tarjeta del diseño a mano: el menú del sistema da accesibilidad y gestos gratis, y el diseño es de Android.
- **Confirmación:** `.confirmationDialog` con los textos de arriba.
- **Foto:** `.photosPicker(isPresented:selection:matching: .images)` de PhotosUI, que no pide permiso de fotos.
  - La imagen se carga con `loadTransferable(type: Data.self)`, se escala y se guarda como en `EpubImporter.saveCover`.
  - En la Mac funciona igual. La API se verifica en la documentación de Apple para iOS 17 y macOS 14.
- **"Leer libro"** llama a `home.open(bookId:)`, que en la Mac ya muestra "El lector llega más adelante a la Mac."
- **ViewModel:** `BookActionsViewModel` (`@MainActor @Observable`) inyectado desde `AppGraph`. Después de cada acción recarga Inicio, la biblioteca y la búsqueda (`ScreenRefs`) y pide el envío de posiciones.

## Tareas

### Tarea 1 (K-172): Android, datos y dominio (M)
- [x] Regla de "no leído" en el estado de lectura (`positionProgression`), con test JVM (LIB-018).
- [x] `BookActions`, `BookEditor` y `BookEditorImpl`, con Hilt. Base de datos v6 (`removed_books`, `coverVersion`) con `MIGRATION_5_6`.
- [x] `addCloudOnly` ignora quitados e importar borra la marca (LIB-020). `coverVersion` llega a `LibraryBook`, y `BookCover` lo usa como clave.
- [x] Guardado de portada compartido con `EpubImporter` (`CoverImages.kt`). La foto se lee con `ImageDecoder`, que respeta la orientación, y en Android 8 con `BitmapFactory`.
- [x] Tests JVM `BookActionsTest`: `markFinished` y `markUnread` guardan la posición correcta y piden el envío. Tests de emulador `BookEditorTest`: quitar, la sincronización no re-agrega un quitado, reimportar y portada. `MigrationTest` 5→6. Los de emulador compilan y no se corrieron (sin emulador).

**Verificación:** `./gradlew :app:testDebugUnitTest`, `compileDebugAndroidTestKotlin`.

### Tarea 2 (K-173): Android, menú y acciones en las tres pantallas (M)
- [x] `BookActionsViewModel` con test JVM: opciones según el estado (`menuActions`), cada acción y sus mensajes.
- [x] `BookActionsMenu` según el diseño, con `placeMenu` y test JVM (arriba, debajo, bordes y área segura).
- [x] `BookActionsHost` con diálogo de confirmación, Photo Picker (`PickVisualMedia`, solo imágenes) y snackbar. Está en la raíz de `AppNavHost`, solo en Inicio, Estantes y Buscar, y el fondo se desenfoca con `Modifier.blur`.
- [x] `Modifier.bookClickable` (tocar o mantener presionado, con vibración y "Más acciones" para TalkBack) y `coverAnchor` en `BookCell`, `ContinueCard`, `RowItem` y `ResultRow`. Íconos de Material Symbols Rounded como vector drawables. Textos en `strings.xml`.
- [x] Test de emulador `BookActionsMenuTest` (LIB-016, LIB-018): tocar abre, mantener presionado muestra el menú y el estado, libro terminado con "no leído", tocar fuera cierra. Compila y no se corrió.
**Verificación:** compila (`assembleDebug`), tests JVM pasan.

### Tarea 3 (K-174): Apple, datos y dominio (M)
- [x] Regla de "no leído" (`unreadLocator`, `positionProgression`) en `ReaderDomain`, y `BookActions` y `BookEditor`, con tests (`swift test`). Una posición sin progreso sigue contando como "nuevo", como antes en Apple.
- [x] `RemovedBookRecord` y `coverVersion` (valores por defecto, migración automática). `LibraryStore` implementa `BookEditor`, `addCloudOnly` salta los quitados y `addImported` borra la marca. La foto se escala con ImageIO (`CoverImages`), en iPhone y en la Mac, respetando la orientación.
- [x] Tests de app `BookActionsDataTests` con `inMemoryContainer()`: terminado y no leído, quitar (registro, posición y archivos), sincronización que no lo trae, reimportar, portada de 600 px y datos que no son imagen.
- Nota: en un worktree nuevo, `xcodebuild` no resuelve Readium sin red. Se copiaron `SourcePackages` y `Package.resolved` de otro worktree y se usó `-disableAutomaticPackageResolution`.

**Verificación:** `swift test` y `xcodebuild … test` en iPhone y en la Mac.

### Tarea 4 (K-175): Apple, menú contextual y acciones (M)
- [ ] `BookActionsViewModel` con tests (fake de `BookActions`).
- [ ] `.contextMenu` en las cuatro vistas, `.confirmationDialog` y `.photosPicker`. Recarga de pantallas y envío de posiciones.
- [ ] Regenerar el proyecto con `xcodegen`.

**Verificación:** tests en iPhone y en la Mac pasan; se ve en el simulador.

### Tarea 5 (K-176): cierre (S)
- [ ] `specs/platforms/android.md` y `apple.md`: notas de las acciones. `KANBAN.md`: K-154 lee `removed_books` y la portada propia para propagar a la nube.
- [ ] Instalar en el teléfono y en el simulador, y verificar a mano con el usuario: las cuatro acciones en las tres pantallas, que un quitado no vuelva tras sincronizar, y que un terminado se vea terminado en el otro dispositivo.

**Verificación:** el usuario lo ve en el teléfono y en el simulador.

## Riesgos

- **Migración de Room 5→6.** Mitigación: `MigrationTest` y una migración aditiva (tabla nueva y columna con valor por defecto).
- **Un libro quitado en este dispositivo sigue en la nube y en los otros.** Ocupa cuota y reaparece si se inicia sesión en un dispositivo nuevo. Es esperado hasta K-154 y está documentado en LIB-020.
- **Portada propia con el libro ya subido.** Los otros dispositivos siguen viendo la anterior. Documentado en LIB-019.
- **Libro quitado mientras otro dispositivo sigue leyendo.** Las posiciones remotas de un id quitado se ignoran, porque `applyRemote` no encuentra el libro. Se cubre con un test.
