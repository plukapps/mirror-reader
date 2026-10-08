# Android — Lector a dos páginas en tablet

**Objetivo:** en una tablet en horizontal, el lector muestra dos páginas lado a lado, como en el diseño T03 (`design/Margin Ebook App.dc.html`, "Android tablet · landscape").

**Specs que implementa:** RDR-016, AND-006. Toca RDR-009 (animación), RDR-010 (pie), RDR-011 (toques en bordes).
**Decisiones técnicas:** ADR 0004 (Readium). No hay decisión nueva: es una preferencia del navegador.

## Alcance acordado (2026-10-08)

- **Entra:** solo el lector a dos páginas. Se activa por tamaño de ventana (AND-006).
- **No entra** (aunque el diseño de tablet lo muestra): riel lateral, Inicio y biblioteca con lista y detalle (T01, T02), panel de notas (T04), búsqueda, marcador, barra de selección, "de 254" y "minutos restantes" en el pie. Los specs vigentes (RDR-010, RDR-013) mandan; lo demás queda para otras rebanadas.

## Decisiones

- **Regla pura:** `useTwoPages(widthDp, heightDp)` en `domain`: ancho ≥ 840, alto ≥ 480 y ancho > alto. Probada en JVM. La ventana se lee con `LocalConfiguration` (cambia al girar o redimensionar).
- **Mapeo a Readium:** `toEpubPreferences(twoPages)` fija `columnCount = TWO` (o `ONE`) y `spread`. La API existe en 3.4.0 (`EpubPreferences.columnCount`, `spread`, verificado con `javap`); falta verificar sus valores exactos en la Tarea 2.
- **Pie:** el navegador informa la posición de la primera página; la segunda se calcula como la siguiente posición (sin pasar de la última). Si no se comporta así en libros reales, se revisa en el checkpoint.
- **Animación y toques:** el par se trata como una sola página: la captura (`PageCapture`) y el movimiento ya son de todo el ancho, así que debería bastar. Se verifica a mano.
- **Girar:** `ReaderViewModel` conserva el `Locator`; al cambiar `columnCount` el navegador repagina y se vuelve a la misma posición.

## Tareas

### Tarea 1: spec y plan (S) — K-068
- [x] RDR-016 en `specs/product/02-reader.md`, AND-006 en `specs/platforms/android.md`, este plan y las tarjetas.
**Verificación:** el usuario aprueba el plan.

### Tarea 2: regla y mapeo a Readium (S) — K-069
- [x] Verificar con `javap` `ColumnCount` y `Spread`.
- [x] `useTwoPages` en `domain` + tests JVM (teléfono vertical y horizontal, tablet vertical y horizontal, ventana angosta, justo en 840/480).
- [x] `toEpubPreferences` recibe si son dos páginas; test de emulador del mapeo (clases de Readium).
**Verificación:** tests JVM. **Depende de:** Tarea 1.

### Tarea 3: lector a dos páginas (M) — K-070
- [x] `ReaderScreen` calcula `twoPages` desde la ventana y reenvía las preferencias al navegador al cambiar.
- [x] Pie con dos posiciones (`pageLabel`, probada en JVM: una página, par, última posición, sin dato).
- [x] Toques en bordes, deslizar y animación (RDR-009, 011) sobre el par.
**Verificación:** tests JVM; en el emulador de tablet (o redimensionable) en horizontal se ven dos páginas y se pasa de a par. **Depende de:** Tarea 2.

### Checkpoint: dos páginas
- [ ] Compila, tests JVM pasan, el teléfono sigue con una página. Revisar contigo.

### Tarea 4: cierre (S) — K-071
- [x] Giro sin perder el lugar. Al probarlo se vio un fallo: Android recrea la actividad y el navegador volvía a la posición del momento de abrir. Arreglo: `NavigatorFragmentHost` arma la fábrica en cada instanciación con la última posición (`NavigatorHost.onLocatorChanged`); test JVM verificado por mutación.
- [x] Ajustes (fuente, tamaño, tema) en ambas páginas, y panel de ajustes sobre el par.
- [x] Dos libros reales (*Dejar ir*, *Zero to One*) en el emulador con tablet simulada.
- [x] Docs al día (`specs/platforms/android.md`).
- [ ] Verificación a mano en tablet real.
**Depende de:** Tarea 3.

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Con `columnCount = TWO` Readium no deja las dos páginas con el mismo margen del diseño | Medio | Ajustar `pageMargins` y el padding en la Tarea 3; verlo en el emulador. |
| La captura de página (RDR-009) o el borde del libro (`BookEdges`) asumen una página | Medio | Revisar `PageCapture` y `NavigatorPageTurnOps`; probar primera y última página a mano. |
| Pasa a dos páginas un teléfono grande en horizontal | Bajo | La regla exige alto ≥ 480 dp; los teléfonos en horizontal miden ~400 dp de alto. |
| Al girar se pierde el lugar | Medio | El `Locator` vive en el ViewModel; escenario de spec y prueba a mano. |
| La posición de la segunda página no es "la siguiente" | Bajo | Se revisa con libros reales en el checkpoint. |

## Fuera de este plan

Riel lateral, Inicio y biblioteca de tablet (T01, T02), panel de notas (T04), búsqueda, marcador, barra de selección, total de páginas y minutos restantes.
