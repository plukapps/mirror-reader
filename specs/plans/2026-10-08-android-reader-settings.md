# Android — Controles del lector: barra superior y panel de ajustes

**Objetivo:** que los controles del lector sean los del diseño (`design/Margin Ebook App.dc.html`, pantallas 06 a 08): una barra superior con volver, capítulo y "Aa", y un panel inferior con los ajustes de lectura.

**Specs que implementa:** RDR-013 (barra superior), RDR-014 (panel de ajustes), y de RDR-002 el tipo de letra y el interlineado. Toca RDR-001, 003, 004 y 009 (cambian de lugar, no de comportamiento).
**Decisiones técnicas:** ADR 0009 (tipografías, provisional), ADR 0004 (Readium).

## Alcance acordado (2026-10-08)

- **Entra:** barra superior (volver, capítulo, "Aa"); panel inferior con tipo de letra (3), A−/A+, tema (Clásico, Sepia, Noche), interlineado (2) y, debajo, Índice y animación de página.
- **No entra:** marcadores y notas (el botón de marcador del diseño), brillo (queda fuera del spec; se decide aparte), márgenes (siguen en K-012), minutos restantes en el título, sincronizar las preferencias (RDR-008).
- **Título de la barra:** solo el capítulo actual, de la tabla de contenidos.
- **Controles de hoy** (Índice, animación E/S): pasan al panel, sin cambiar su comportamiento. El modo Scroll se eliminó después (K-066, 2026-10-08): el lector es siempre paginado.

## Decisiones

- **Una sola fuente de verdad:** `ReaderSettings` suma `fontFamily` (`ReaderFont`: `SERIF`, `SANS`, `MONO`) y `lineSpacing` (`LineSpacing`: `NORMAL`, `WIDE`), guardados en DataStore junto a lo que ya está. El panel solo llama a los métodos de `ReaderViewModel`.
- **Mapeo a Readium** en `EpubPreferencesMapper`: `fontFamily`, `lineHeight` y `publisherStyles = false` (ADR 0009). Valores iniciales del interlineado: normal 1,4 y amplio 1,7; se ajustan a ojo con libros reales.
- **Fuentes propias:** Newsreader y JetBrains Mono en `assets/fonts/` (Host Grotesk ya está en `res/font`; se copia a assets para servirla al navegador). Se declaran en `EpubNavigatorFragment.Configuration` (`servedAssets` y declaraciones de familia). La API exacta se verifica con `javap` en la Tarea 3 antes de escribir código.
- **Panel:** `ModalBottomSheet` de Material 3 con el estilo del diseño (fondo `#130000`, esquinas 28 dp, asa de 36×4 dp, acento `#FBD256`). Con el panel abierto, un toque en el libro lo cierra y no pasa de página.
- **Título del capítulo:** se deduce de la posición actual (`href` del locator) contra la tabla de contenidos aplanada (`flattenToc`): la entrada más profunda cuyo recurso coincide, o la última anterior en el orden de lectura si el recurso no tiene entrada propia. Lógica pura en `domain`, probada en JVM.
- **Volver:** `onBack` ya existe en `ReaderScreen`; se conecta al botón de la barra. Con el panel abierto, atrás cierra el panel primero.
- **Captura de página (RDR-009):** hoy el paso de página espera a que la barra se oculte antes de capturar. Con el panel abierto no se pasa de página (el toque cierra el panel), así que no interfiere; se verifica a mano.

## Tareas

### Tarea 1: spec, ADR y plan (S) — K-060
- [x] RDR-013 y RDR-014 en `specs/product/02-reader.md`; ADR 0009; este plan; tarjetas en el Kanban.
**Verificación:** el usuario aprueba el plan. **Depende de:** nada.

### Tarea 2: modelo de ajustes (S) — K-061
- [x] `ReaderFont`, `LineSpacing` y sus setters en `ReaderSettings`; persistencia en `SettingsRepositoryImpl` (claves nuevas con valor por defecto, sin migración).
- [x] Tests JVM: valores por defecto, cada setter cambia solo su campo, ida y vuelta por el repositorio con DataStore falso (el de DataStore real corre en emulador).
**Verificación:** tests JVM. **Depende de:** Tarea 1.

### Tarea 3: tipografías y mapeo a Readium (M) — K-062
- [x] Verificar la API con `javap` (`FontFamily`, `Configuration.servedAssets`, `addFontFamilyDeclaration`, `EpubPreferences.lineHeight/publisherStyles`).
- [x] Fuentes en `assets/fonts/`, declaración en `NavigatorFragmentHost`, mapeo en `EpubPreferencesMapper`.
- [x] Test de emulador del mapeo (clases de Readium). Escrito y compilado; se corre con permiso del usuario.
**Verificación:** compila; en el teléfono, cada fuente y cada interlineado se ven en el libro. **Depende de:** Tarea 2.

### Tarea 4: barra superior y título del capítulo (M) — K-063
- [x] `currentChapterTitle(toc, href, readingOrder)` en `domain` + tests JVM (capítulo con entrada, recurso sin entrada, libro sin tabla de contenidos → vacío, subcapítulos).
- [x] `ReaderViewModel` expone el título en `Ready`; `ReaderTopBar` (volver, título, "Aa") reemplaza la fila de texto de `ReaderControls`.
**Verificación:** tests JVM; en el teléfono, el título cambia al pasar de capítulo y volver regresa a la pantalla anterior. **Depende de:** Tarea 2.

### Tarea 5: panel de ajustes (L) — K-064
- [x] `ReaderSettingsSheet` con las filas del diseño y las de Lectura (Índice, modo, animación); "Aa" abre y cierra y se ve activo.
- [x] Reglas: un toque en el libro con el panel abierto lo cierra sin pasar de página; atrás cierra el panel; elegir Índice cierra el panel.
- [x] Tests de ViewModel (cada acción cambia el estado y se persiste); pruebas de pantalla en emulador escritas y compiladas.
**Verificación:** tests JVM; en el teléfono, recorrido de los 8 escenarios nuevos de RDR-013/014. **Depende de:** Tareas 3 y 4.

### Checkpoint: panel completo
- [ ] Compila, tests JVM pasan, el paso de página y su animación (RDR-009) siguen igual. Revisar contigo.

### Tarea 6: cierre (S) — K-065
- [ ] `specs/platforms/android.md` y `AGENTS.md` al día; K-012 reducida a márgenes; verificación a mano completa en el teléfono.
**Depende de:** Tarea 5.

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| `publisherStyles = false` cambia el aspecto de todos los libros (se pierde la fuente y el estilo del editor) | Alto | Es la decisión de ADR 0009, con Newsreader por defecto como el diseño. Probar con los 4 libros reales; si molesta, agregar "Original del libro". |
| Las fuentes propias no cargan en el navegador (rutas de `servedAssets`) | Medio | Verificar con `javap` y probar en el teléfono en la Tarea 3, antes de construir la interfaz. |
| La captura de página (RDR-009) toma el panel o la barra | Medio | Con el panel abierto no hay paso de página; revisar a mano con el panel y sin él. |
| El título del capítulo falla con tablas de contenidos raras (sin entrada para el recurso, anidadas) | Bajo | Función pura con tests de esos casos; el título vacío es válido. |
| Tocar fuera del panel pasa de página sin querer | Medio | Regla explícita y escenario de spec; se prueba a mano. |

## Fuera de este plan

Marcadores y notas (botón del diseño), brillo, márgenes, minutos restantes, opción "Original del libro", sincronizar las preferencias con la cuenta (RDR-008).
