# Plan — Búsqueda local (Android)

**Objetivo:** que "Buscar" de la barra inferior abra una pantalla de búsqueda sobre la biblioteca, como la pantalla "03 — Search" de `design/Margin Ebook App.dc.html`.

**Specs que implementa:** LIB-006, LIB-013, LIB-014, LIB-015 (`specs/product/01-library.md`); cambia HOM-005 y HOM-006 (`specs/product/07-home.md`): "Buscar" deja de mostrar el aviso de "llega más adelante".
**Rama:** `feature/android-search` (worktree `.claude/worktrees/android-search`). **Tarjetas:** K-110 a K-113. Pedido del usuario (2026-10-09): "las búsquedas por ahora son locales".

## Diseño (medidas del HTML, pantalla 03)

- Fondo `Paper`. Título "Buscar" de 44 sp, interletra −0,045 em, margen 20 dp a los lados y 12 dp arriba (igual que el saludo de Inicio).
- Campo: 16 dp a los lados y arriba, alto 56 dp, radio 28 dp, fondo `#FDFDFD`, borde de 2 dp tinta, relleno 18 dp; lupa de 22 dp a la izquierda, texto 16 sp medio y, con texto, una cruz de 20 dp `Muted` para borrar.
- Filtros: chips de 34 dp de alto, radio 17 dp, relleno 14 dp, 8 dp entre ellos, 14 dp arriba. Activo: fondo tinta y texto amarillo; inactivo: borde de 1,5 dp tinta. Texto 13 sp semibold.
- "N resultados": 11 sp semibold en mayúsculas, interletra 0,08 em, `Muted`, 22 dp arriba y 6 dp abajo.
- Fila de resultado: portada 52 × 76 dp, 14 dp entre portada y texto, 12 dp arriba y abajo, línea `Line` de 1 dp entre filas. Título 16 sp bold, autor 13 sp `Muted`, estado 11 sp semibold; a la derecha un `chevron_right` de 22 dp.
- Estado "En biblioteca · 42 %" del diseño con un punto de 6 dp `#C9A100`: como todos los resultados están en la biblioteca, el estado dice el progreso ("Leyendo · 42 %") y el punto aparece solo en los que estoy leyendo; si no, "Nuevo", "Terminado" o "En la nube".

## Decisiones

- **Solo local.** Se busca en la lista de libros que ya expone `LibraryRepository.books` (Room), filtrada en memoria. Con bibliotecas de cientos de libros no hace falta índice ni consulta SQL. Sin conexión funciona igual (LIB-013).
- **Coincidencia (LIB-013):** se normaliza texto y consulta (minúsculas, sin acentos con `Normalizer` NFD, espacios colapsados). La consulta se parte en palabras; un libro coincide si cada palabra aparece en el título o en el autor. Con "Autores", cada palabra tiene que estar en el autor.
- **Orden (LIB-015):** primero los libros cuyo título empieza con la consulta, después el resto de las coincidencias por título, después las de solo autor; dentro de cada grupo, el orden de la biblioteca (el último importado primero).
- **Filtros:** del diseño quedan "Todo" y "Autores". "En mi biblioteca" y "Gratis" son del catálogo, fuera de alcance; sin catálogo "Todo" y "En mi biblioteca" serían iguales.
- **Colección:** LIB-006 también pide buscar por colección; llega con LIB-005 (K-008).
- **Estado de la pantalla:** consulta y filtro en `SavedStateHandle`, para que sobrevivan a la rotación. Sin consulta, la pantalla muestra una ayuda corta en lugar de resultados; con consulta sin coincidencias, "Sin resultados para «…»".
- **Teclado:** el campo toma el foco al entrar por primera vez; la acción del teclado es "Buscar" y lo cierra. Al tocar un resultado se abre el lector (y al volver, la búsqueda sigue ahí).
- **Ícono:** `chevron_right` y `close` de Material Symbols Rounded copiados como `ImageVector`, igual que `NavIcons`.
- **Accesibilidad (HOM-007):** cada fila es un botón con título, autor y estado leídos juntos; los chips tienen rol de pestaña con estado seleccionado; la cruz tiene descripción "Borrar búsqueda".

## Tareas

### Tarea 1 (K-110): búsqueda en el dominio (S)
- [x] `domain/search/BookSearch.kt`: `SearchScope { All, Authors }`, `normalizeForSearch(text)` y `searchBooks(books, query, scope)`.
- [x] Tests JVM `BookSearchTest` (LIB-013, LIB-015): acentos y mayúsculas, varias palabras, filtro Autores, orden, consulta vacía, libro sin autor.
**Verificación:** `./gradlew :app:testDebugUnitTest --tests '*BookSearchTest'`.

### Tarea 2 (K-111): `SearchViewModel` (S)
- [x] `ui/search/SearchViewModel.kt` con `StateFlow<SearchUiState>` (consulta, filtro, resultados, `hasQuery`), consulta y filtro en `SavedStateHandle`.
- [x] Tests JVM `SearchViewModelTest` con biblioteca falsa: resultados al escribir, cambio de filtro, sigue los cambios de la biblioteca (progreso que cambia), consulta guardada.
**Verificación:** tests JVM pasan.

### Tarea 3 (K-112): pantalla y navegación (M)
- [x] `ui/search/SearchScreen.kt` (`SearchScreen` con ViewModel y `SearchContent` sin él) según el diseño.
- [x] `SearchIcons` (`chevron_right`, `close`).
- [x] Ruta `search` en `AppNavHost`; "Buscar" de la barra navega y queda marcado. Solo "Perfil" muestra el aviso (HOM-006).
- [x] Textos en `strings.xml`.
- [x] Test de emulador `SearchScreenTest` (LIB-014, LIB-015, HOM-005): escribir muestra resultados y cantidad, "Autores" filtra, tocar un resultado avisa el id, sin coincidencias muestra el aviso. Se compila; no se corre en el teléfono sin preguntar.
**Verificación:** compila (`assembleDebug`, `compileDebugAndroidTestKotlin`), tests JVM pasan.

### Tarea 4 (K-113): cierre (S)
- [x] `specs/platforms/android.md`: nota de la búsqueda.
- [x] Instalar en el teléfono (`installDebug`) y verificar a mano con el usuario: buscar por título, por autor con y sin acentos, filtro Autores, abrir un resultado y volver.
**Verificación:** el usuario lo ve en el teléfono.

## Resultado

283 tests JVM pasan (14 nuevos: `BookSearchTest` y `SearchViewModelTest`). `SearchScreenTest` (4 casos) compila, sin correr. Instalada en el teléfono (SM-S711B) y en la tablet (SM-X510) el 2026-10-09: la pantalla se ve como el diseño y buscar "ha" encuentra *Los 7 hábitos…* primero, sin importar la tilde. El resto de la prueba a mano (filtro Autores, abrir un resultado y volver) queda para la revisión del PR.
