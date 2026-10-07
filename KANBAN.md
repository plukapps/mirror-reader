# Kanban

Reglas en `AGENTS.md`. Máximo 1 tarjeta en "En curso".
Plan de referencia: `specs/plans/2026-10-06-android-epub-viewer.md`

## Backlog

- **K-008** Biblioteca local de EPUB (importar, metadatos, colecciones, búsqueda). Requiere plan propio. LIB-001 a LIB-008.
- **K-009** Anotaciones: marcadores, subrayados, notas. Requiere plan propio. ANN-001 a ANN-007.
- **K-010** Cuenta y sincronización. Requiere plan propio, y revisar ADR 0003 antes. ACC, SYN.
- **K-011** Cumplimiento con Google Play y beta cerrada. CMP.
- **K-012** Ajustes de lectura restantes: tipo de letra, interlineado, márgenes. RDR-002.

## Listo

- **K-024** Quitar el padding vertical de Readium (40 dp arriba y abajo en modo paginado) y reservar la barra de estado desde la pantalla. RDR-002. Rama `feat/page-turn-animation`.

## En curso


## Revisión

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

- Specs v0 escritos y aprobados (`specs/`).
