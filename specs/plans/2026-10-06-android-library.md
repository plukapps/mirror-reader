# Android — Biblioteca local como pantalla de inicio

**Objetivo:** que la biblioteca sea la pantalla de inicio de Android, según la pantalla "05 — Library" de `design/Margin Ebook App.dc.html`. Se importan EPUB, se ven en una grilla con su portada y progreso, y al tocar uno se abre en el lector.

**Spec que implementa:** `specs/product/01-library.md`: LIB-001 (selector de archivos), LIB-002, LIB-003, LIB-004 (lectura de metadatos; la edición queda fuera), LIB-010 y LIB-011 (nuevos, ver abajo). Plataforma: AND-001.

## Cambios de spec (se aprueban con este plan)

Se agregan a `specs/product/01-library.md`, sin nombrar tecnología:

- **LIB-010** La biblioteca debe poder filtrarse por estado de lectura: Todos, Leyendo y Terminados. Un libro sin abrir no está en "Leyendo" ni en "Terminados".
- **LIB-011** Cada libro de la grilla debe mostrar portada, título y su progreso de lectura. Un libro al 100 % se marca como terminado y uno sin abrir como nuevo.

Escenario: Dado un libro al 42 %, cuando abro "Leyendo", entonces aparece; cuando abro "Terminados", no.

## Fuera de esta rebanada (siguen en Backlog)

Colecciones (LIB-005), búsqueda (LIB-006), nube y descarga (LIB-007), eliminar/quitar (LIB-008), cuota (LIB-009), edición de metadatos, "Compartir / Abrir con" (AND-003), pestaña "Notas" del diseño (depende de anotaciones, K-009), barra inferior con Buscar y Perfil, pantalla de detalle del libro, PDF. La barra inferior no se construye: solo hay una pantalla.

## Decisión técnica (ADR 0006, provisional)

Al importar, el EPUB se **copia** al almacenamiento privado de la app (`files/books/<hash>.epub`). Así la biblioteca no depende de permisos de URI que el proveedor puede revocar, funciona sin conexión (ADR 0002) y el hash de contenido (LIB-003) da el nombre del archivo. Alternativa descartada: guardar solo la URI persistente, que falla si el usuario mueve o borra el archivo. Costo: el libro ocupa el doble hasta que el usuario borre el original.

## Estructura destino

```
domain/model/       LibraryBook (id=hash, título, autor, portada, progreso, estado), ReadingStatus
domain/repository/  LibraryRepository (observar libros, importar, abrir por id)
domain/usecase/     ImportBooksUseCase, ReadingStatus derivado del progreso
data/local/db/      BookEntity, BookDao (migración Room 1→2 con exportSchema)
data/library/       EpubImporter (copia + hash + metadatos + portada), LibraryRepositoryImpl
ui/library/         LibraryScreen, LibraryViewModel (StateFlow<LibraryUiState>), BookCover
ui/navigation/      startDestination = library; ruta reader por bookId
```

El lector hoy se abre por `uri`. Pasa a abrirse por `bookId` y el repositorio resuelve el archivo local. La posición guardada ya usa `bookId`; hay que verificar que sea el mismo hash.

## Tareas

### Tarea 1: spec y ADR (S)
- [ ] LIB-010 y LIB-011 en `01-library.md`, ADR 0006, índice de `specs/README.md`.
**Verificación:** revisión del usuario.

### Tarea 2: modelo, Room e importación (M)
- [ ] `EpubImporter` copia el archivo, calcula SHA-256, lee título, autor y portada con Readium, y rechaza corruptos y con DRM con un mensaje claro (LIB-002, LIB-003, LIB-004).
- [ ] Importar el mismo archivo dos veces no duplica y avisa (escenario de LIB-003).
- [ ] `BookEntity` y `BookDao` con migración 1→2; portada guardada como archivo en `files/covers/`.
**Verificación:** tests JVM del cálculo de estado y del hash; tests en emulador de Room y del importador con el fixture (`make_fixture_epub.py`), referenciando LIB-002/003/004.

### Tarea 3: repositorio y progreso (M)
- [ ] `LibraryRepository` expone `Flow<List<LibraryBook>>` combinando libros y posiciones guardadas (porcentaje desde el `Locator`).
- [ ] Estado derivado: nuevo (sin posición), leyendo, terminado (100 %). LIB-010, LIB-011.
- [ ] El `bookId` de la posición coincide con el hash. Si hoy usa otra cosa, se migra o se documenta.
**Verificación:** tests JVM con repositorios falsos.

### Tarea 4: pantalla de biblioteca (L)
- [ ] `LibraryScreen` según el diseño 05: título "Biblioteca", botón "Importar" (selector múltiple, LIB-001), pestañas Todos N / Leyendo N / Terminados N, grilla de 3 columnas con portada, barra de progreso y etiqueta Nuevo/Terminado. Tema y tipografía del diseño (amarillo `#FBD256`, negro `#130000`, Schibsted Grotesk).
- [ ] Portada generada (color + título) cuando el EPUB no trae una.
- [ ] Estados: vacío con llamada a importar, importando, error con mensaje.
- [ ] Textos en español en recursos de strings. Etiquetas de accesibilidad (AND-002).
- [ ] Teléfono y tablet: columnas adaptables (AND-001).
**Verificación:** tests de `LibraryViewModel` en JVM; test Compose en emulador; preview. Revisión visual contra el diseño.

### Tarea 5: biblioteca como inicio y apertura por id (S)
- [ ] `startDestination` pasa a la biblioteca; `HomeScreen` se elimina. Tocar un libro abre el lector por `bookId`.
- [ ] Volver del lector refresca el progreso.
**Verificación:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`, y verificación a mano en el teléfono con tus EPUB.

## Tarjetas del Kanban

K-008 se divide en K-029 a K-033, una por tarea. K-008 queda en Backlog con el resto de LIB-005 a LIB-009 como "Biblioteca, segunda rebanada".

## Riesgos

- Portadas grandes en memoria: se reducen al importar.
- Importar muchos EPUB a la vez bloquea la UI: se hace en `Dispatchers.IO` con estado de progreso.
- Cambiar el lector de `uri` a `bookId` toca código con tests de emulador; hay que correrlos. Según tus notas, esos tests no se corren en el teléfono sin preguntarte y no hay emulador, así que te aviso antes de ese paso.
