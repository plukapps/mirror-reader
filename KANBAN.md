# Kanban

Reglas en `AGENTS.md`. Máximo 1 tarjeta en "En curso".
Plan de referencia: `specs/plans/2026-10-06-android-epub-viewer.md`

## Backlog

- **K-008** Biblioteca, segunda rebanada: colecciones, búsqueda, nube, eliminar/quitar, cuota, edición de metadatos, "Compartir / Abrir con". LIB-005 a LIB-009. Requiere plan propio.
- **K-009** Anotaciones: marcadores, subrayados, notas. Requiere plan propio. ANN-001 a ANN-007.
- **K-010** Cuenta y sincronización. Requiere plan propio. Backend Firebase (ADR 0007), código en `code/backend/v1`. Actualizar ADR 0005 (SDK de Firebase en lugar de Retrofit). ACC, SYN.
- **K-047** Backend, Tarea 4: cuota de espacio (spike de reglas de Storage, plan B con Cloud Function). ACC-002, ACC-003, LIB-009.
- **K-048** Backend, Tarea 5: cuentas con email y Google, documento de usuario con plan gratuito. ACC-001, ACC-002, ACC-005.
- **K-049** Backend, Tarea 6: despliegue al proyecto `dev` y cierre de docs.
- **K-011** Cumplimiento con Google Play y beta cerrada. CMP.
- **K-012** Ajustes de lectura restantes: tipo de letra, interlineado, márgenes. RDR-002.

## Listo

- **K-038** Inicio, Tarea 4: cierre de docs y verificación a mano. Rama `feature/home`.
- **K-024** Quitar el padding vertical de Readium (40 dp arriba y abajo en modo paginado) y reservar la barra de estado desde la pantalla. RDR-002. Rama `feat/page-turn-animation`.

## En curso


## Revisión

- **K-046** Backend, Tarea 3: reglas de Storage. ACC-004, LIB-002, LIB-008. Plan `2026-10-07-backend-firebase.md`. Rama `feature/backend`. Pasan 38 tests en el emulador; verificado quitando validaciones a propósito. De paso se corrigió la concurrencia de los tests (compartían emulador). Falta que lo revises.
- **K-045** Backend, Tarea 2: modelo de datos y reglas de Firestore. ACC-004, ACC-005, ACC-002, SYN-006, SYN-007, LIB-005. Plan `2026-10-07-backend-firebase.md`. Rama `feature/backend`. Modelo en `specs/platforms/backend.md`. Pasan 27 tests en el emulador; verificado quitando validaciones a propósito. Falta que lo revises.
- **K-043** Aviso de fin de lectura: detectar el fin del cuerpo por la tabla de contenidos y mostrar un toast. RDR-012. Plan `2026-10-07-android-reading-end.md`. Rama `feature/home`. Compila y pasan los tests JVM (`BackMatterTest`). Detección por palabras clave del título (ajustada con 4 libros reales: la primera versión solo acertaba 1 de 4). Instalada en el teléfono; falta probarla a mano.
- **K-042** Fuente de la app: Host Grotesk (variable, normal e itálica) como tipografía por defecto del tema. Rama `feature/home`. Compila; falta verla a mano.
- **K-041** Inicio secciones, Tarea 3: UI de Leyendo, Agregados recientemente y Terminados. HOM-008 a 011. Rama `feature/home`. Compila y pasan los tests JVM. Instalada en el emulador; falta verla a mano. `HomeScreenTest` escrito y compilado, sin correr (pedido del usuario).
- **K-040** Inicio secciones, Tarea 2: ruta de la biblioteca con filtro. HOM-011. Rama `feature/home`. Compila y pasan los tests JVM. 
- **K-039** Inicio secciones, Tarea 1: reglas puras (`addedAt`, `reading`, `recentlyAdded`, `finished`). HOM-008 a 010. Plan `2026-10-07-android-home-sections.md`. Rama `feature/home`. Compila y pasan los tests JVM. 
- **K-037** Inicio, Tarea 3: pantalla de Inicio (continuar, para ti, vacíos). HOM-001 a 004, 007. Rama `feature/home`. Compila y pasan los tests JVM (`HomeViewModelTest`). Falta verla a mano y test Compose de emulador (no escrito).
- **K-036** Inicio, Tarea 2: barra inferior de 4 destinos y navegación. HOM-005, 006, 007. Rama `feature/home`. Compila y pasan los tests JVM. Instalada en el dispositivo conectado. Falta `HOM-005/006` en test Compose de emulador (no escrito) y verla a mano.
- **K-035** Inicio, Tarea 1: modelo y reglas puras (`lastReadAt`, `homeContent`, saludo). HOM-001 a 004. Plan `2026-10-07-android-home.md`. Rama `feature/home`. Pasan los tests JVM (`HomeContentTest`). Sin commit.
- **K-033** Biblioteca, Tarea 5: biblioteca como inicio y lector por `bookId`. Rama `feature/library`. Compila y pasan los tests JVM. Instalado en el teléfono; falta verificar a mano. `ReaderScreenTest` adaptado (bookId = hash) y sin correr.
- **K-032** Biblioteca, Tarea 4: pantalla de biblioteca según diseño 05. LIB-001, 010, 011, AND-001, AND-002. Rama `feature/library`. Compila y pasan los tests JVM. Faltan correr `LibraryScreenTest` en emulador y verla en el teléfono contra el diseño.
- **K-031** Biblioteca, Tarea 3: repositorio y progreso. LIB-010, 011. Rama `feature/library`. Pasan los tests JVM. La progresión se guarda junto a la posición (migración 1→2 ampliada).
- **K-030** Biblioteca, Tarea 2: modelo, Room e importación (copia, hash, metadatos, portada). LIB-002, 003, 004. Rama `feature/library`. Compila y pasan los tests JVM. Faltan correr en emulador `EpubImporterTest` y `MigrationTest`.
- **K-029** Biblioteca, Tarea 1: spec (LIB-010, LIB-011) y ADR 0006. Plan `2026-10-06-android-library.md`. Rama `feature/library`. Falta que lo apruebes.
- **K-028** Bug: el paso de página se dispara sin querer al querer ver los controles (zonas de borde del 30% por lado) o al seleccionar texto (pulsación larga + arrastre cuenta como deslizar). RDR-011. Rama `feat/page-turn-animation`. Falta verificar a mano en el teléfono.
- **K-026** Bug: en la primera página no se puede retroceder, ni avanzar en la última. Hoy el arrastre y el toque en el borde animan igual aunque la página no cambie. RDR-009. Rama `feat/page-turn-animation`. Falta verificar a mano en el teléfono.
- **K-025** Retroceder de página es el rollback del avance: la página anterior entra desde la izquierda encima, y la actual queda debajo con paralaje hacia la derecha. El aclarado del fondo oscuro se aplica a la que entra. RDR-009. Rama `feat/page-turn-animation`.
- **K-001** Proyecto base con Readium y fixture de prueba. Plan, Tarea 1. Compila con Readium 3.4.0 (AGP 9.1.0, Gradle 9.3.1, compileSdk 37, desugaring).
- **K-002** Cargar un EPUB desde una URI. Plan, Tarea 2. LIB-002.
- **K-003** Mostrar el libro en pantalla. Plan, Tarea 3. RDR-007, AND-003.
- **K-004** Ajustes de lectura: paginado o scroll, tema, tamaño. Plan, Tarea 4. RDR-001, 002, 003.
- **K-005** Tabla de contenidos y progreso. Plan, Tarea 5. RDR-004, 005.
- **K-006** Recordar posición y ajustes. Plan, Tarea 6. RDR-006.
- **K-007** Verificación manual con EPUB de ejemplo y cierre de docs. Plan, Tarea 7. Verificado en emulador con el EPUB de prueba (abre, tema oscuro, A+, ocultar barra). Pendiente: recorrer la lista manual con tu EPUB real.
- **K-014** Hilt, esqueleto de capas y navegación de una sola actividad. Plan `2026-10-06-android-architecture.md`, Tarea 1.
- **K-015** Capa de datos: Room (posición) y DataStore (ajustes) con repositorios. Plan `2026-10-06-android-architecture.md`, Tarea 2.
- **K-016** Pantalla del lector en Compose, reemplaza a `ReaderActivity`. Plan `2026-10-06-android-architecture.md`, Tarea 3.
- **K-017** Pruebas con Hilt, limpieza y cierre de docs. Plan `2026-10-06-android-architecture.md`, Tarea 4.
- **K-018** Animación de paso de página (deslizar con paralaje). Rama `feat/page-turn-animation`. RDR-009.
- **K-019** La animación de página sigue al dedo y se completa o se cancela al soltar (umbral). Rama `feat/page-turn-animation`. RDR-009.
- **K-020** Bug: al empezar el swipe de página la animación se traba y luego salta al dedo. La captura tardaba en estar lista y no seguía al dedo mientras el navegador cambiaba de página. Ahora se captura al apoyar el dedo, los controles se ocultan sin fade y el cierre desacelera. RDR-009. Rama `feat/page-turn-animation`.
- **K-021** Número de página en el pie del lector (modo paginado, solo posición actual). RDR-010. Rama `feat/page-turn-animation`.
- **K-022** El preview de Compose del lector usa el `ReaderContent` real y solo reemplaza el navegador de Readium por una página de texto. Rama `feat/page-turn-animation`.
- **K-023** Margen de página de Readium en 0 (`pageMargins`), para que el texto use todo el ancho y alto disponibles. RDR-002. Rama `feat/page-turn-animation`.

## Hecho

- **K-044** Backend, Tarea 1: estructura de `code/backend/v1`, emuladores y proyecto de tests de reglas. Plan `2026-10-07-backend-firebase.md`. Rama `feature/backend`. Pasan 3 tests de humo en el emulador (reglas que deniegan todo). Requiere JDK 21+ (ver README). Aprobada.
- **K-034** Inicio: spec `specs/product/07-home.md` (HOM-001 a HOM-007), aprobado.
- Specs v0 escritos y aprobados (`specs/`).
