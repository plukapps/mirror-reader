# Plan — Planes de suscripción: pantalla de planes y avisos de cuota (Android e iOS)

**Objetivo:** que la app muestre los planes (P01), avise cuando un libro no entra en el espacio (P02), marque las funciones pagas al importar (P03b), avise al 80 % y tenga "Mi plan" (P04), todo sin cobrar todavía.

**Specs que implementa:** PLN-001 a PLN-013 (`specs/product/12-plans.md`). Cambia PRF-006.
**Diseño:** `design/Margin Planes.dc.html` (P01, P02, P03b, aviso del 80 %, P04 Gratis). Se lee el HTML para medidas, colores y radios; se renderiza sirviendo `design/` por HTTP local (con `file://` no corre su JS).
**Pedido del usuario (2026-10-13):** "empecemos a trabajar en la screen donde se presentan los planes" y los modales que bloquean o avisan, en Android y en iOS, con PR separados. Decisiones tomadas con el usuario:
- Sin compras: los botones de compra y "Restaurar compras" avisan (PRF-003). Precios de ejemplo fijos.
- La cuota sigue en bytes (ADR 0008). No hay cambios de backend.
- Entran P02 (cupo lleno y libro que no entra), el aviso del 80 %, P04 Mi plan (Gratis) y P03 (funciones pagas; solo la variante de importar, porque exportar notas no existe todavía).

**Espacio de los planes pagos:** Lector 5 GB y Biblioteca 50 GB, confirmados por el usuario "por ahora" (2026-10-13). Los precios siguen siendo los de ejemplo del diseño (open-questions #1).

## Ramas y PR

| Rama | Contenido | PR contra |
|---|---|---|
| `feature/subscription-plans` | diseño importado, spec, plan, Kanban (K-217) | base de las dos de abajo |
| `feature/subscription-plans-android` | K-218 a K-222 | `develop` |
| `feature/subscription-plans-ios` | K-223 a K-227 | `develop` |

Las dos ramas de plataforma salen de `feature/subscription-plans`, así cada PR lleva el spec. Son independientes y se fusionan en cualquier orden (el segundo verá solo su código).

## Tareas (iguales en las dos plataformas)

### 1. Catálogo y P01 Planes (K-218 Android, K-223 iOS) — PLN-001 a PLN-006, PLN-013

- **Dominio:** `PlanCatalog` con los tres planes (`PlanId { FREE, READER, LIBRARY }`, nombre, bajada, espacio, incluye, precio mensual y anual como texto, equivalencia por mes) y `PlanComparison` (filas de la tabla). `Billing { MONTHLY, ANNUAL }`. Funciones puras con test: `ctaFor(plan, billing)` ("Probar Lector 14 días gratis" / "Elegir Lector · $2.99 por mes") y su subtítulo, y `usageLine(quota)` reutilizando `storageUnitFor`/`formatStorage` (PRF-006).
- **ViewModel:** estado `billing` (arranca anual), `selected` (arranca Lector), `tableOpen`, `usage` (de `QuotaCache`, sin red). Tests JVM / Swift Testing.
- **UI:** pantalla completa sin barra inferior. Android: ruta `plans` en `AppNavHost`. iOS: se presenta con `.fullScreenCover` desde la raíz (se abre desde Perfil, Mi plan, Biblioteca y las hojas), con un `PlansPresenter` (`@Observable`) compartido. Tablet (≥ 600): tres columnas. Barra de compra fija al pie. Cruz para cerrar. Letra serif en itálica de "Si cancelás…": Newsreader (Android: `FontFamily` desde los assets del lector).
- **Íconos:** Material Symbols Rounded (Android con `materialSymbol`, iOS como assets de plantilla, igual que Perfil): close, check, radio_button_checked/unchecked, expand_more/less, verified_user.

### 2. P02 Bloqueos de cuota (K-219, K-224) — PLN-007 a PLN-009

- **Dominio:** `quotaBlock(quota: StorageQuota?, pendingBytes, bookBytes): QuotaBlock?` → `Full` o `DoesNotFit(bookBytes, freeBytes)` o nada. Test de los bordes (sin cuota, justo entra, lleno, varios pendientes).
- **Cuota fresca:** `UploadBooks` guarda en `QuotaCache` la cuota que lee en cada pasada (así la Biblioteca y la comprobación usan un valor reciente). Android: `QuotaCache` expone además un `Flow` (DataStore). iOS: la Biblioteca relee la caché cuando termina una pasada.
- **Disparo:** después de importar (Biblioteca, Inicio, "Abrir con" en Android; `fileImporter` en iOS), el ViewModel que importó pide la comprobación con los libros importados y los pendientes de subir, y si hay bloqueo emite un evento con título y tamaños. La hoja se muestra en la raíz (Android: host en `AppNavHost`, como `BookActionsHost`; iOS: `.sheet` en `RootView`). "Ver planes" cierra la hoja y abre P01.
- **UI:** hoja inferior con el estilo de `FontSheet` (radio 28, `Field`, manija). P02a: ícono cloud_off, título, texto, comparación Gratis/Lector, botón "Ver planes" con flecha, "Ahora no". P02b: fila del libro con portada, ícono upload_file.
- **Android, de paso:** `HomeViewModel.onImport` no pide una pasada de sincronización después de importar (la Biblioteca sí). Se agrega `librarySync.request()` para que el libro suba enseguida, con test.

### 3. P03 Importar con funciones pagas (K-220, K-225) — PLN-010

- "Importar" (Biblioteca e Inicio) abre la hoja "Importar libros" en lugar del selector de archivos. "Desde este dispositivo" cierra la hoja y abre el selector de hoy. "Google Drive" y "Calibre / OPDS" (marca "Biblioteca") abren la hoja de función paga con "Ver planes" y "Ahora no". En la Mac, sin importador, la hoja no se muestra y sigue el aviso de hoy.
- Componente de hoja de función paga reutilizable (ícono, título, texto, "Incluido en {plan}"), listo para P03a cuando existan las notas.
- Tests: ViewModel de la hoja (qué fila abre qué) y, en Android, un test de Compose de la hoja.

### 4. Aviso del 80 % (K-221, K-226) — PLN-011

- **Dominio:** `shouldShowUsageNotice(quota, dismissed)`: uso ≥ 80 % y < 100 %, y no descartado.
- **Preferencia:** `plans_usage_notice_dismissed` (DataStore / `UserDefaults`). Se borra al cerrar sesión (es de la cuenta; agregar a `LocalDataWiper` / `LocalAccountWiper`).
- **UI:** tarjeta arriba de la lista de la Biblioteca (debajo de la línea de sincronización), con "Ver planes" subrayado y cruz.
- Tests: regla pura, ViewModel de la Biblioteca (aparece, se descarta, no vuelve).

### 5. P04 Mi plan y Perfil (K-222, K-227) — PLN-012, PRF-006

- Pantalla Mi plan (Gratis) con su ViewModel (lee `QuotaCache` y refresca con `QuotaSource`, como Perfil). Android: ruta `profile/plan`. iOS: `ProfileRoute.myPlan`, sin barra inferior.
- Perfil: "Mejorar plan" abre P01; tocar el bloque del espacio abre Mi plan.
- Tests: ViewModel de Mi plan; `ProfileScreenTest` (Android) con las dos acciones nuevas.

## Verificación

Cada PR: tests en verde (Android `:app:testDebugUnitTest` y compilación de los de emulador; Apple `swift test` y app en iPhone y Mac), capturas de P01 (teléfono y tablet), P02a, P02b, P03, aviso y P04 en claro y oscuro, notas de la plataforma, tarjetas en Revisión. La prueba a mano va en el teléfono o el iPhone del usuario, sin tests instrumentados ahí sin preguntar. Para forzar P02 con 15 MB alcanza con importar un EPUB más grande que el espacio libre.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| La caché de cuota está vieja y la hoja aparece (o no) sin razón | Se refresca en cada pasada; el servidor sigue siendo el que decide (LIB-009) y la línea de sincronización sigue igual |
| Vender funciones que no existen (exportar, Drive, familia) | No hay compra en esta rebanada; antes de cobrar, revisar el catálogo con lo que exista (CMP) |
| P03 agrega un paso a importar | Es lo que pide el diseño; "Desde este dispositivo" queda primero |
| Los precios fijos no coinciden con la tienda | Son de ejemplo; la rebanada de compras los toma de la tienda |
| Dos PR tocan `KANBAN.md` | Cambios acotados; conflictos menores al fusionar |

## Notas de implementación (Android)

Rama `feature/subscription-plans-android`, K-218 a K-222 y, en la segunda parte, K-250 a K-252.

- **Dominio** (`domain/plans`): `PlanCatalog` (ids, espacio, tamaño por archivo y precios de ejemplo; los textos viven en `strings.xml`, como las preguntas frecuentes; `maxFileBytes`, `planOf`), `ctaFor` (siempre "Elegir…"), `quotaBlock` (`TooLarge`, `Full`, `DoesNotFit`), `ImportQuotaCheck`, `PlanPrompts` (una hoja a la vez, `@Singleton`), `shouldShowUsageNotice`, `UsageNoticePreference`, `effectiveReaderSettings` y `allowReadingOption` (`ReadingFeatures.kt`). `storageUsage` (en `domain/profile/StorageFormat.kt`) lo comparten Perfil, Planes, el aviso y Mi plan.
- **Cuota fresca:** `QuotaCache.quota` es un `Flow`. `UploadBooksUseCase` guarda la cuota que lee en cada pasada con libros pendientes, y al final la misma con lo que subió sumado (el servidor la actualiza unos segundos después). Sin libros pendientes la pasada no lee la cuota: la refrescan Perfil y Mi plan.
- **Hojas:** `PlanPromptHost` en la raíz (`AppNavHost`), fuera del onboarding. Tras "Abrir con" el lector se abre igual y la hoja aparece encima. "Ver planes" cierra la hoja y abre P01.
- **P03:** la hoja "Importar libros" (PLN-010) se quitó en la segunda parte: "Importar" abre el selector de archivos. Queda la hoja de función paga (`PaidContent`), hoy solo para temas y fuentes (PLN-015).
- **Plan de la cuenta (segunda parte):** `PlanCatalog.planOf` lee `quota.plan` de la caché (`free`, `reader`, `library`; los dos últimos todavía no los escribe el backend). Sin cuota o con otro valor, Gratis.
- **Tamaño por archivo (PLN-014):** se comprueba al importar antes que el espacio, también sin cuota leída (con el máximo de Gratis), y la pasada de subida no sube los libros que lo superan: cuentan en `notEnoughSpace` ("N libros no entran en tu plan"). En Biblioteca, que no tiene plan siguiente, la hoja no muestra la tarjeta amarilla.
- **Temas y fuentes (PLN-015):** `ReaderViewModel` y `ProfileViewModel` muestran los ajustes efectivos y exponen `readingOptionsLocked`. El navegador se instala con los efectivos. Las opciones pagas llevan `LectorMark` (en el panel del lector, dentro del botón de letra y arriba a la derecha del círculo; en Perfil, debajo del círculo) y TalkBack las anuncia con "Incluido en Lector". La hoja de función paga sale de la raíz, encima del panel del lector.
- **Desvíos del diseño:**
  - La proporción de uso de P01 es un porcentaje en monoespaciada ("80 %"); el diseño muestra "8/10" porque contaba libros.
  - Tabla del teléfono: columnas de 54, 70 y 62 dp (el diseño usa 50, 50 y 56 px; más anchas para que "Sin tope" y "100 MB" entren en una línea).
  - La marca "Lector" de las opciones pagas y el ícono de la hoja de temas y fuentes (`palette`) no están en el diseño.
  - P02b muestra un recuadro "EPUB" en lugar de la portada, como el diseño.
- **Tests:** 466 JVM (51 nuevos en total). Compose, sin correr (no hay emulador): `PlansScreenTest`, `PlanSheetsTest`, `MyPlanScreenTest`, casos nuevos en `ProfileScreenTest` y `LibraryScreenTest`. `ImportSheetTest` se quitó con la hoja.

## Segunda parte: funciones por plan (2026-10-13)

**Pedido del usuario:** una tabla de funciones por plan (prueba gratis no por ahora, tamaño máximo por archivo 2 MB / 50 MB / 100 MB, temas y fuentes y exportar notas en Lector y Biblioteca; estadísticas, Drive/Calibre/email, lector web y familia en definición). Decisiones: (1) en Gratis el lector usa solo Clásico y serifa, el resto abre la hoja de función paga; (2) el tope por archivo primero solo en las apps; (3) no mostrar funciones en definición.

**Specs:** PLN-001, PLN-006 y PLN-012 cambian; PLN-010 se retira; nuevos PLN-014 (tamaño por archivo), PLN-015 (temas y fuentes), PLN-016 (ocultar lo que está en definición). Cambian RDR-014, PRF-013 y PRF-014. Va en los mismos PR (#34 Android, #35 iOS).

| Android | iOS | Qué |
|---|---|---|
| K-250 | K-253 | Catálogo: tamaño por archivo en `Plan`, sin prueba gratis (`ctaFor` siempre "Elegir…"), tarjetas y tabla de PLN-001, Mi plan con "Archivos de hasta 2 MB" y su tarjeta, quitar la hoja "Importar libros" (Importar abre el selector) y los textos de Drive, Calibre, familia, estadísticas y lector web. La hoja de función paga queda para PLN-015. |
| K-251 | K-254 | PLN-014: `PlanCatalog.maxFileBytes(plan)` y `planOf(quota)` (desconocido → Gratis); `quotaBlock` gana `TooLarge(bookBytes, maxBytes)`, comprobado antes que el espacio; hoja P02b de archivo muy grande; `UploadBooks` saltea los que superan el máximo y los cuenta como `notEnoughSpace`; texto de la línea de sincronización "N libros no entran en tu plan". Tests de los bordes (justo 2 MB, plan desconocido, Lector). |
| K-252 | K-255 | PLN-015: `effectiveReaderSettings(saved, plan)` pura (Gratis → Clásico y serifa, sin tocar lo guardado); el lector aplica la efectiva; en el panel del lector y en Ajustes de lectura de Perfil, las opciones pagas llevan la marca "Lector" y abren la hoja "Leé con tus temas y fuentes" (con "Ver planes" a P01, también desde el lector). Tests: función pura, ViewModel del lector y de Perfil (tocar Sepia en Gratis no cambia la preferencia y pide la hoja). |

Riesgo: el plan de la cuenta sale de la caché de cuota; sin dato, todo se comporta como Gratis (el caso real de hoy: no hay planes pagos).
