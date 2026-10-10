# Apple — Lector de EPUB en iPhone

**Objetivo:** que en el iPhone se pueda importar un EPUB o abrir uno de la cuenta y leerlo, con los mismos controles que Android: barra superior, panel de ajustes, índice, número de página, aviso de fin de lectura y posición guardada.

**Spec:** `specs/product/02-reader.md` (RDR-001 a RDR-007, RDR-010 a RDR-015), `specs/product/01-library.md` (LIB-001 a LIB-004, LIB-007), `specs/platforms/apple.md`. Diseño: `design/Margin Ebook App.dc.html`, pantallas 06 a 08, con los valores que ya usa Android (`code/android/.../ui/reader/`). Decisión técnica: ADR 0013 (nuevo).

**Rama:** `feature/ios-reader`, desde `master`.

## Decisiones (del usuario, 2026-10-09)

- **Origen de los libros, como Android:** se importan desde Archivos (y con "Abrir con" desde otra app) o se bajan de la nube al tocar un libro "solo en la nube" (LIB-007).
- **Sin animación propia de paso de página:** se usa el paso de página que trae Readium. RDR-009 queda como tarjeta aparte.
- **Posición solo local:** se guarda en la base del iPhone (RDR-006). Subirla a la nube, el diálogo "¿Continuar desde…?" (SYN-003) y el aviso "Seguir desde" (SYN-013) van en otra tarea.

## Decisiones técnicas

- **Motor:** Readium Swift Toolkit 3.9.0 (ADR 0004, se registra en ADR 0013; 3.10 y 3.11 exigen Xcode 16.3), por Swift Package Manager en `project.yml`. Readium Swift solo soporta iOS, así que el paquete se enlaza solo en iOS (`destinationFilters`) y el código que lo usa va con `#if os(iOS)`. **En la Mac no hay lector ni importación todavía**: al tocar un libro se avisa que llega más adelante.
- **Archivos:** `Application Support/books/{hash}.epub`, como Android (ADR 0006). Hash SHA-256 del contenido con CryptoKit (LIB-003). Portada en JPEG de hasta 600 px de alto (LIB-004).
- **Bajada de la nube:** a un temporal desde `users/{uid}/books/{bookId}.epub` (Storage) y recién al terminar se instala, como `DownloadBookUseCase`.
- **Ajustes de lectura:** `UserDefaults` (ADR 0012), mismas claves y valores que Android: tema (Clásico, Sepia, Noche), letra (Newsreader, Host Grotesk, JetBrains Mono), tamaño en décimas (0,5 a 2,5, paso 0,1), interlineado (1,4 y 1,7). Se mapean a `EPUBPreferences` como en `EpubPreferencesMapper.kt`: sin scroll, una columna, `publisherStyles` desactivado, márgenes 0,2. La sincronización de ajustes (RDR-008) queda fuera, como en Android.
- **Fuentes del libro:** las tres variables de `assets/fonts` (ADR 0009) viajan en la app y se declaran a Readium con `fontFamilyDeclarations`.
- **Reglas puras en el dominio**, con los casos de test de Android: `currentChapterTitle`, `backMatterStart` y `BodyEndDetector` (RDR-012), `pageLabel` (una página), pasos de tamaño de `ReaderSettings`.
- **Pantalla:** `EPUBNavigatorViewController` envuelto en SwiftUI. Toques: 20 % izquierdo y derecho pasan de página, 60 % central muestra u oculta los controles (RDR-011). Con los controles ocultos se oculta la barra de estado (RDR-015). Una sola página siempre (iPhone, RDR-016 no aplica).
- **Ubicaciones:** el locator se guarda como JSON de Readium, compatible con el de Android (ADR 0004, 0011).

## Fuera de alcance

- Animación de paso de página propia (RDR-009). Tarjeta K-135.
- Subir la posición a la nube, diálogo de reanudar y aviso "Seguir desde" (SYN-002, SYN-003, SYN-013). Tarjeta K-136.
- Subir a la nube los libros importados en el iPhone (SYN-001, LIB-009). Tarjeta K-137.
- Lector e importación en la Mac (Readium Swift no la soporta). Sigue en K-079.
- Pantalla de biblioteca: la pestaña sigue mostrando el aviso de HOM-006. Se importa desde Inicio y con "Abrir con".
- Márgenes (K-012) y brillo, como en Android.

## Tareas

- **K-128** Docs y dependencia: ADR 0013, este plan, `apple.md`, Kanban. Readium en `project.yml` (solo iOS) y verificar que compila con Xcode 16.2.
- **K-129** Dominio: `ReaderSettings`, `currentChapterTitle`, `backMatterStart`, `BodyEndDetector`, `pageLabel`, `ImportOutcome`, protocolos de archivo de libro, posición y ajustes. Tests con `swift test` que repiten los de Android.
- **K-130** Datos: archivos de libros, importador (copia con hash, validación y metadatos con Readium, portada), bajada desde Storage, posición local en `PositionRecord`, ajustes en `UserDefaults`. Tests en el simulador con el EPUB de `make_fixture_epub.py`.
- **K-131** Importar y abrir: botón "Importar un EPUB" de Inicio con el selector de Archivos, "Abrir con" (tipo de documento EPUB), avisos del resultado, libros de Inicio que abren el lector (con bajada si hace falta).
- **K-132** Lector: `ReaderViewModel` (cargando, error, listo; guarda la posición con 250 ms de espera), navegador de Readium, toques, barra superior (volver, capítulo, "Aa"), número de página, índice, aviso de fin de lectura, barra de estado oculta con los controles. Tests del ViewModel con repositorios falsos.
- **K-133** Panel de ajustes según el diseño: tipo de letra, A− y A+, tema, interlineado, índice. Sin el interruptor de animación (no hay animación propia todavía).
- **K-134** Cierre: docs, resultado en este plan y verificación a mano en el simulador de iPhone (y en el iPhone físico si está a mano).

## Verificación

- `swift test` en `Packages/ReaderDomain`.
- `xcodebuild test` en el simulador de iPhone; build de la Mac sin errores.
- A mano en el simulador: importar el EPUB de prueba y uno real, abrir un libro de la nube (se baja), pasar páginas con toques, ver y ocultar controles, cambiar letra, tamaño, tema e interlineado, saltar por el índice, cerrar y reabrir en el mismo lugar, ver el aviso de fin de lectura.
