# Plan — Búsqueda local (iPhone)

**Objetivo:** que "Buscar" de la barra inferior de iPhone abra la búsqueda sobre la biblioteca, como la pantalla "03 — Search" de `design/Margin Ebook App.dc.html` y como ya hace Android (plan `2026-10-09-android-search.md`).

**Specs que implementa:** LIB-006, LIB-013, LIB-014, LIB-015 (`specs/product/01-library.md`); HOM-005 y HOM-006 (`specs/product/07-home.md`) en iPhone: "Buscar" deja de mostrar el aviso.
**Rama:** `feature/ios-search` (worktree `.claude/worktrees/ios-search`). **Tarjetas:** K-119 a K-122. Pedido del usuario (2026-10-09): seguir el diseño y la implementación de Android.

## Diseño

Mismas medidas que Android (sección "Diseño" de su plan), en pt: título "Buscar" 44 pt; campo de 56 pt de alto, radio 28, fondo `#FDFDFD`, borde de 2 pt tinta, lupa de 22 pt y cruz de 20 pt `Muted`; chips "Todo" y "Autores" de 34 pt (activo tinta con texto amarillo, inactivo borde de 1,5 pt); "N RESULTADOS" 11 pt; filas con portada de 52 pt de ancho, título 16 pt bold, autor 13 pt, estado 11 pt con el punto `#C9A100` si lo estoy leyendo, y chevron de 22 pt. Íconos: los mismos trazados de Material Symbols Rounded que Android, como SVG en `Assets.xcassets/Search`.

## Decisiones

- **Misma regla que Android.** `searchBooks` en el paquete `ReaderDomain` es una traducción directa de `BookSearch.kt`: minúsculas, sin acentos (`folding` con `.diacriticInsensitive`), palabras que tienen que estar todas en el título o el autor, orden por prefijo de título, título y autor. Los tests son los mismos casos que `BookSearchTest`.
- **Solo local**, sobre `LibraryRepository.books()` (SwiftData), que incluye los libros solo en la nube (LIB-013).
- **Estado "En la nube" (LIB-014):** `LibraryBook` suma `isDownloaded` (por defecto verdadero), que `LibraryStore` toma de `BookRecord`. Hoy en Apple todos los libros llegan de la nube, así que todos dicen "En la nube".
- **Recarga:** la pantalla lee la biblioteca cada vez que aparece; la sincronización de Inicio sigue siendo la única que trae la nube.
- **Consulta y filtro** viven en el `SearchViewModel`, que arma `RootView` una sola vez: sobreviven al cambio de pestaña. El campo toma el foco solo la primera vez.
- **Tocar un resultado** no hace nada todavía: Apple no tiene lector (como los libros de Inicio). Se conecta con la rebanada del lector.
- **Mac:** sin barra inferior no hay cómo llegar a Buscar; espera la navegación de la Mac (K-078).
- **Accesibilidad (MAC-002, HOM-007):** cada fila se lee como un solo elemento con título, autor y estado (pasa a ser botón cuando abra el lector); los chips llevan el rasgo de seleccionado; la cruz se llama "Borrar búsqueda".

## Tareas

### Tarea 1 (K-119): búsqueda en el dominio (S)
- [x] `ReaderDomain/BookSearch.swift`: `SearchScope`, `normalizeForSearch`, `searchBooks`.
- [x] `LibraryBook.isDownloaded` y su mapeo en `LibraryStore`.
- [x] `BookSearchTests` (LIB-013, LIB-015), mismos casos que Android.
**Verificación:** `swift test` en `Packages/ReaderDomain`.

### Tarea 2 (K-120): `SearchViewModel` (S)
- [x] `App/UI/Search/SearchViewModel.swift`: consulta, filtro, resultados, `hasQuery`, `load()`.
- [x] `searchStatus(_:)` (texto del estado) en `SearchFormatting.swift`.
- [x] Tests: resultados al escribir, cambio de filtro, recarga ve cambios de la biblioteca, estado de cada libro.
**Verificación:** tests de la app en el simulador de iPhone.

### Tarea 3 (K-121): pantalla y barra (M)
- [x] `SearchView` según el diseño; íconos `SearchClose` y `SearchChevron`.
- [x] `TabBarModel`: Buscar disponible; `RootView` muestra Inicio o Buscar según el destino. Ajustar `TabBarModelTests`.
**Verificación:** compila para iPhone y Mac; tests pasan en los dos destinos.

### Tarea 4 (K-122): cierre (S)
- [x] `specs/platforms/apple.md` y `code/apple/README.md` al día.
- [x] Verificación en el simulador: buscar por título, por autor con y sin tildes, filtro Autores, sin resultados.
- [ ] A mano: escribir, cambiar de filtro, cambiar de pestaña y volver (la consulta sigue).

## Resultado

Tests: 34 del dominio (8 nuevos de `BookSearchTests`, vistos en rojo con un stub y verificado por mutación del orden), 32 de la app en iPhone 16 y 31 en la Mac (7 nuevos de búsqueda y 1 de la barra). En la Mac los tests solo arrancan con `ENABLE_HARDENED_RUNTIME=NO` por un problema de firma que esta rama no toca (K-123).

Verificado en el simulador (iPhone de 375 pt) con la biblioteca real (25 libros): ayuda sin consulta y foco en el campo, "ha" da 6 resultados con portada, autor y estado, "HABITOS" encuentra *Los 7 hábitos…* sin importar la tilde ni las mayúsculas, "Autores" filtra (punto amarillo en "Leyendo · 20 % · En la nube"), "zzz" muestra "Sin resultados para «zzz».". Las capturas salieron de builds temporales con la consulta precargada: sin permiso para manejar el simulador, no se probó tocando la barra, escribiendo ni cambiando de pestaña y volviendo. Queda para la prueba a mano.
