# Android — Secciones de Inicio: Leyendo, Agregados recientemente y Terminados

**Objetivo:** completar la home según el diseño actualizado "02 — Home". Reemplaza "Para ti" (HOM-004, retirado).

**Spec que implementa:** `specs/product/07-home.md`: HOM-008 a HOM-011.

## Decisiones

- "Leyendo" excluye el libro de "Continuar leyendo".
- "Agregados recientemente" incluye todos los libros, por fecha de importación.
- "Ver todo" abre la biblioteca con el filtro de la sección (argumento opcional de la ruta).
- "Mes" de Terminados: el de `lastReadAt` (último guardado de posición). Es una aproximación: si se reabre un libro terminado, el mes cambia.

## Tareas

### Tarea 1: reglas puras (S) — K-039
- [ ] `LibraryBook.addedAt`; `HomeContent` con `reading`, `recentlyAdded`, `finished` (máx. 5) y totales.
**Verificación:** `HomeContentTest`, `HomeViewModelTest` (JVM).

### Tarea 2: navegación con filtro (S) — K-040
- [ ] Ruta `library?filter=`; `LibraryViewModel` toma el filtro inicial de `SavedStateHandle`.
**Verificación:** `LibraryViewModelTest` (JVM).

### Tarea 3: UI de las tres secciones (M) — K-041
- [ ] Filas con título, contador y "Ver todo"; tarjetas según el diseño; insignia de check; "Autor · Mes". Textos en `strings.xml`, accesibilidad, preview.
**Verificación:** `:app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`; captura en el dispositivo. Los tests de emulador se escriben pero no se corren (pedido del usuario).
