# Plataforma — Apple: Mac e iOS (borrador)

Mac primero, iOS después, desde el mismo proyecto y el mismo target. Distribución: Mac App Store y App Store. Hoy existen Inicio y la biblioteca con los libros reales de la cuenta (planes `specs/plans/2026-10-09-ios-home-data.md` y `2026-10-09-ios-library.md`) y, en iPhone, la búsqueda local (plan `specs/plans/2026-10-09-ios-search.md`) y el lector de EPUB con importación (plan `specs/plans/2026-10-09-ios-reader.md`). La Mac todavía no lee ni importa: Readium Swift solo soporta iOS (ADR 0013).

## Alcance

El mismo que Android: todo lo definido en `product/`. Se construye por rebanadas después de la versión de Android (ADR 0001).

## Requisitos específicos (borrador, a confirmar en la primera rebanada real)

- **MAC-001** Debe funcionar en una ventana redimensionable, con el contenido legible desde 480 pt de ancho.
- **MAC-002** Debe ser compatible con VoiceOver y usable con teclado.
- **MAC-003** Debe abrir EPUB desde el Finder ("Abrir con" y arrastrar a la app o al Dock) (LIB-001).
- **MAC-004** Debe cumplir las reglas de la Mac App Store (sandbox, eliminación de cuenta, CMP-001).

Requisitos de iOS (borrador, plan `specs/plans/2026-10-09-ios-layout.md`):

- **IOS-001** Debe verse completa en un iPhone en vertical desde 375 pt de ancho: sin contenido cortado ni scroll horizontal de la pantalla, respetando las áreas seguras (isla, barra de estado, indicador de inicio).
- **IOS-002** La barra de estado debe leerse sobre el fondo de la app.

## Arquitectura

Stack y capas en `specs/adr/0010-apple-stack-and-architecture.md` (provisional): SwiftUI, MVVM en capas con el dominio en un paquete Swift local, proyecto generado con XcodeGen, Swift Testing. Base local con SwiftData y SDK de Firebase para Apple: `specs/adr/0012-apple-persistence-and-firebase.md` (provisional). Motor de EPUB: Readium Swift Toolkit 3.9.0, solo en iOS (`specs/adr/0013-apple-epub-engine.md`, provisional).

## Decisiones

- Versión mínima: macOS 14 e iOS 17. Un solo target con los dos destinos.
- Interfaz con Host Grotesk y la paleta del diseño, como Android.
- Onboarding (plan `2026-10-12-ios-onboarding.md`, ADR 0015, K-160 a K-166): el de Android, O1 a O9, en iPhone y Mac. Alta con email o Google (GoogleSignIn), verificación por enlace, intereses, meta y recordatorio diario (notificación local), ingreso y recuperación de contraseña. O9 abre en la app solo con Associated Domains (K-167); "Iniciar sesión con Apple" falta antes de publicar (K-168). Sin Firebase, la app abre en Inicio y funciona en local.
- Inicio con datos reales (K-107 a K-111): sesión iniciada en el onboarding (antes, cuenta de desarrollo), libros, posiciones y portadas bajados de Firebase a la base local. Desde K-145 a K-153 también sube (ver Sincronización).
- Inicio (prueba, K-076): mismo contenido que en Android, centrado con un ancho máximo de 640 pt.
- Barra inferior (HOM-005, K-096): en iPhone, la misma que Android (píldora flotante, el contenido pasa por debajo). Inicio, Buscar, Estantes y Perfil navegan. En la Mac no hay barra: su navegación (probablemente barra lateral) espera un diseño (K-078), y hasta entonces la búsqueda no se alcanza desde la Mac.
- Búsqueda local (LIB-006, LIB-013 a LIB-015, K-119 a K-122): misma regla que Android (`searchBooks` en `ReaderDomain`, mismos casos de test) y la pantalla 03 del diseño. Tocar un resultado abre el libro en el lector (iPhone).
- Biblioteca (K-124 a K-127): la de Android según el diseño 05 (pestañas Todos, Leyendo y Terminados, grilla con progreso y "En la nube"). En iPhone es el destino Estantes; en la Mac se abre desde "Ver todo" de Inicio, con volver. "Ver todo" abre la biblioteca con el filtro de la sección (HOM-011). "Importar" abre el selector de Archivos en iPhone (plan `2026-10-09-ios-reader.md`) y no aparece en la Mac. Tocar un libro lo abre en el lector; en la Mac avisa que todavía no hay. Bajo el título, la línea de estado de la sincronización (SYN-008), como Android.

- Lector (K-128 a K-134, ADR 0013): Readium Swift Toolkit 3.9.0 solo en iOS. Se abre desde Inicio, la biblioteca y la búsqueda; si el libro está solo en la nube, se baja antes (LIB-007). Importar desde Archivos y "Abrir con". Barra superior, panel de ajustes, índice, número de página y aviso de fin como Android. Sin animación propia de página (K-135). La posición viaja entre dispositivos (ver Sincronización).

- Sincronización (plan `2026-10-10-ios-sync.md`, K-145 a K-153; falta la prueba a mano, K-155): la misma pasada que Android (`LibrarySync` en `ReaderDomain`): libros de la nube, subida de los importados (archivo, documento y portada, con cuota, LIB-009), portadas en los dos sentidos, descarga automática de los libros de la nube (comprobando el hash) y posiciones pendientes. La orquesta `SyncScheduler`: al arrancar, al volver a la app con TTL de 5 minutos, al importar y al tocar el aviso de error; una pasada a la vez. Estado visible en la biblioteca (SYN-008). Metadatos como Android: la subida pisa título y autor y la bajada solo agrega libros (cambios y borrados desde la nube, K-154). Posición como Android (ADR 0011, `PositionSync`): se envía al cerrar el libro o pasar a segundo plano, se escucha mientras la app se ve, se consulta al abrir con "¿Continuar desde…?" si el salto pasa del 2 % y se avisa "Seguir desde [dispositivo]" si otro va más de 0,5 % adelante. El nombre del dispositivo es el modelo ("iPhone").

- Acciones del libro (LIB-016 a LIB-020, SYN-015):
  - **Menú:** es el contextual del sistema (`.bookContextMenu`), con vista previa de la portada, el título y "Autor · estado". En el iPhone se abre manteniendo presionado y en la Mac con clic secundario.
  - **Portada propia:** `PhotosPicker`, con la imagen escalada por ImageIO.
  - **Igual que Android:** la regla de no leído, `RemovalSync` y `LibraryWatcher` en `ReaderDomain`, y `SyncCoordinator` para la escucha.
  - **Portada de un libro bajado:** un libro bajado de la nube sin portada la saca del EPUB (`CoverExtractingBookFiles`), como Android.
  - Plan `specs/plans/2026-10-12-library-actions.md`.

- Perfil (PRF-001 a PRF-019, diseño 09, K-195 a K-198): la pantalla de Android en iPhone (`App/UI/Profile`); en la Mac compila pero no se alcanza (sin barra, K-078).
  - **Reglas comunes:** el formato de tamaños, el tamaño de letra en puntos y las preguntas frecuentes están en `ReaderDomain`, con los mismos casos que Android.
  - **Espacio:** caché en `UserDefaults` (`UserDefaultsQuotaCache`).
  - **Ajustes de lectura:** los mismos de `UserDefaultsReaderSettings`.
  - **Cerrar sesión:** `SignOut` (dominio) usa `SyncScheduler.whileIdle` mediante `CoordinatorSyncControl`. `LocalAccountWiper` borra SwiftData, los archivos y las claves de la cuenta en `UserDefaults` (se listan las que se borran: el dominio también guarda claves del sistema). Sale de Firebase y de `GIDSignIn`.
  - **Ayuda:** las preguntas se abren en un `NavigationStack` propio del tab.
  - **Solo con Wi-Fi (PRF-023, K-208):** `canSync` y `NetworkType` en `ReaderDomain`; `SyncScheduler` no corre la pasada sin red apta y publica `waitingForWifi` ("Esperando Wi-Fi para sincronizar." en la biblioteca, sin reintentar); `DownloadBook` no baja un libro solo en la nube. `PathNetworkMonitor` (`NWPathMonitor`: Wi-Fi o cable) alimenta `SyncNetworkGate`, que guarda `sync.wifiOnly` en `UserDefaults` (del dispositivo: cerrar sesión no la borra). `SyncCoordinator` tampoco escucha ni envía posiciones ni libros quitados sin red apta; al llegar el Wi-Fi con la app a la vista (o al apagar la opción) escucha y pide una pasada. Al abrir un libro sin red apta se usa la posición local.
  - **Apariencia (PRF-021, PRF-022, K-206):** `Appearance` en `ReaderDomain`, guardada en `UserDefaults` (`appearance`, del dispositivo: cerrar sesión no la borra). `MarginColors` son colores dinámicos (`UIColor`/`NSColor` con valor claro y oscuro) que SwiftUI resuelve con el `colorScheme` del entorno; los rellenos oscuros usan `inkFill` y lo que va sobre amarillo, `onYellow`. La raíz aplica `.preferredColorScheme` (nil para Sistema); el onboarding y O9 fuerzan `.environment(\.colorScheme, .light)` y la raíz pide claro mientras no hay sesión; el lector fuerza claro sus colores de la app y elige su barra de estado según su tema. La Mac sigue clara hasta tener Perfil (K-078).
  - **Cambiar contraseña (PRF-020, K-204):** destino `ProfileRoute.changePassword` del `NavigationStack` del tab, sin barra inferior, con los campos del onboarding. `PasswordRepository` y `canSaveNewPassword` en `ReaderDomain`; `FirebaseAccountRepository` reautentica con `EmailAuthProvider.credential` y llama a `updatePassword(to:)`. Una actual incorrecta (`.invalidCredentials`) se muestra bajo ese campo.
  - **Tests de la Mac:** no arrancan por la firma del framework del paquete (K-201).
  - Plan `specs/plans/2026-10-12-profile.md`.
- Interruptores de funciones (ACC-011, ADR 0016, K-321):
  - **Dominio:** `Feature`, `RemoteFlagSource` y el actor `FeatureFlags` en `ReaderDomain`.
  - **App:** `FirebaseRemoteFlagSource` (`FirebaseRemoteConfig` 12.14.0). Señal `plan` desde `QuotaCache`.
  - **Cuándo escucha:** con la app a la vista (`SyncCoordinator.appBecameActive` y `appWentToBackground`). Sin Firebase y en los tests, los valores por defecto.
  - **Template:** `FeatureTemplateTests` exige cada `Feature` en `remoteconfig.template.json`.

- Notas sobre texto y detalle del libro (ANN-003 a ANN-011 en parte, LIB-022, K-322 a K-327; plan `2026-10-14-ios-notes.md`):
  - **Datos:** `Note` y `NoteRepository` en `ReaderDomain`; `NoteRecord` en SwiftData (modelo nuevo, migración automática) y la API de notas en `LibraryStore`. Quitar un libro y cerrar sesión borran sus notas.
  - **Lector (solo iOS):** menú propio sobre la selección (Nota, Copiar, Compartir, diseño 06). `shouldShowMenuForSelection` devuelve `false` y el lector muestra `SelectionMenu` sobre `selection.frame`; el menú se va cuando la selección desaparece. No se usa `EditingAction` propia: Readium 3.9 la agrega con `UIMenuController` y iOS 26 ya no la muestra (K-332). Subrayados con `apply(decorations:in: "notes")`; `observeDecorationInteractions` abre la nota. Editor en `NoteEditorSheet`.
  - **Abrir en una nota:** la fábrica del lector recibe `noteId`; `ReaderViewModel` abre en su locator sin el aviso de otro dispositivo.
  - **Detalle:** `BookDetailView` desde "Ver detalle" del menú contextual: pantalla completa en el iPhone, con el lector encima; hoja en la Mac, que avisa que el lector llega más adelante.

- Sincronización de notas (SYN-016, SYN-017, SYN-005, PRF-009, K-328 a K-331):
  - **Base:** `NoteRecord.deletedAt` y `syncedAt` (la versión que la nube tiene; nil si nunca llegó), migración automática. Borrar una nota que la nube nunca tuvo la elimina; si no, la marca. `NoteSyncStore` aplica lo de la nube solo si es más nuevo.
  - **Sincronizar:** actor `NoteSync` en `ReaderDomain`. Sube las pendientes al guardar en el lector, al final de `LibrarySync` y al prenderse la función, y escucha `users/{uid}/notes` desde `sync.lastNotesSeenAt` (`FirestoreNotes`) con la app a la vista (`SyncCoordinator`). Apagada (`notes_sync_enabled`) o sin red permitida, no hace nada.
  - **Cerrar sesión:** `hasPending` cuenta las notas sin subir; con la función apagada, todas.
- Cierres inesperados (CMP-010, ADR 0018, K-335): `FirebaseCrashlytics` 12.14.0. `CrashReporter` y `CrashReportIdentity` en `ReaderDomain`; `CrashReportSession` (App/Data/Remote) escucha el estado de Firebase Auth y manda el `uid`, o vacío sin sesión. Arranca en `AppGraph` solo con Firebase (nunca en los tests). `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym` y la fase final `Crashlytics` de `project.yml` sube los dSYM; sin `GoogleService-Info.plist` no hace nada. Plan `specs/plans/2026-10-15-crashlytics.md`.

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en una Mac real, y la app aprobada en la Mac App Store.
