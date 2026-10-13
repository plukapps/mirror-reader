# Plataforma — Android (v1)

Primera plataforma en construirse. Distribución: Google Play.

## Alcance

Todo lo definido en `product/`: biblioteca, lector, anotaciones, sincronización, cuenta y cumplimiento.

## Requisitos específicos

- **AND-001** Debe funcionar en teléfono y tablet.
- **AND-002** Debe ser compatible con TalkBack y respetar el tamaño de fuente del sistema.
- **AND-003** Debe recibir EPUB desde "Compartir / Abrir con" (LIB-001).
- **AND-004** Debe cumplir las políticas de Google Play (incluida la eliminación de cuenta, CMP-001).
- **AND-005** La pantalla del lector es una pantalla de Compose dentro de una sola actividad. Ver ADR 0005.
- **AND-006** Dos páginas en el lector (RDR-016) cuando la ventana tiene al menos 840 dp de ancho y 480 dp de alto, y es más ancha que alta. Se decide por el tamaño de la ventana, no por el aparato, así sirve para plegables y multiventana.

## Arquitectura

Stack y capas definidos en `specs/adr/0005-android-stack-and-architecture.md`: Compose, Navigation Compose, MVVM en capas (ui, domain, data, di), Hilt, Coroutines y Flow, Room, DataStore y, para el backend, Retrofit, OkHttp y Gson.

## Decisiones

- Versión mínima de Android: 26 (Android 8.0).
- Idiomas de la interfaz: pendiente (open-questions #4).
- Tipografías del lector (RDR-014): Newsreader, Host Grotesk y JetBrains Mono viajan en `assets/fonts` y se sirven al libro con Readium. ADR 0009 (provisional). Con una fuente elegida se apagan los estilos del editor.
- Controles del lector (RDR-013, RDR-014): barra superior (`ReaderTopBar`) y panel inferior (`ReaderSettingsSheet`, un `ModalBottomSheet`). Plan `specs/plans/2026-10-08-android-reader-settings.md`.
- Dos páginas en el lector (RDR-016, AND-006): `useTwoPages` decide por el tamaño de la ventana; `toEpubPreferences(twoPages)` fija `columnCount`/`spread` y, con dos páginas, `pageMargins = 1.0` (separa las páginas, ≈ 100 dp al centro). Al girar Android recrea el navegador: `NavigatorFragmentHost` arma la fábrica en cada instanciación con la última posición conocida (el `ReaderViewModel` se la informa), no la del momento de abrir. Plan `specs/plans/2026-10-08-android-tablet-spread.md`.
- Búsqueda (LIB-006, LIB-013 a LIB-015): local, sobre `LibraryRepository.books` filtrada en memoria con `searchBooks` (`domain/search`), sin índice ni consulta SQL. Normaliza con `Normalizer` NFD para ignorar acentos. Destino `search` de la barra inferior. Plan `specs/plans/2026-10-09-android-search.md`.
- Arranque y bienvenida (WEL-001 a WEL-008): el arranque sale del tema, sin `core-splashscreen`. En Android 12 o superior lo arman `windowSplashScreenBackground` y `windowSplashScreenAnimatedIcon`; en Android 8 a 11, `windowBackground` con un `layer-list`. Nada lo retiene. `MainViewModel.start` decide una vez por arranque la pantalla inicial; desde el onboarding la regla es ONB-019 (ver abajo). Plan `specs/plans/2026-10-09-android-welcome.md`.
- Onboarding y cuenta (ONB-001 a ONB-022, ADR 0014): Firebase Auth con email y contraseña, y Google con Credential Manager (`requestGoogleIdToken`, cliente web `default_web_client_id`). `MainViewModel.start` decide entre bienvenida (sin sesión), verificación (cuenta de email sin verificar) e Inicio, con la sesión que ya conoce Firebase; la marca `welcomeCompleted` ya no se usa. La verificación y la contraseña nueva van por enlace: la app recibe `https://<proyecto>.firebaseapp.com/__/auth/action` como App Link (`parseAuthLink`) cuando el host publica `assetlinks.json`; si no, lo resuelve la página de Firebase. Intereses, meta y recordatorio viven en DataStore; el recordatorio es una alarma inexacta diaria (`AlarmReminderScheduler`) que se reprograma al reiniciar. Ya no hay cuenta de desarrollo en Android: se entra por las pantallas. Para Google en otra máquina o en release hay que registrar su SHA-1 en Firebase. Plan `specs/plans/2026-10-10-android-onboarding.md`.
- Acciones del libro (LIB-016 a LIB-020, SYN-015, diseño 05b):
  - **Menú:** se abre manteniendo presionada una portada en Inicio, Estantes o Buscar. Es un `Popup` propio en la raíz de `AppNavHost` (`BookActionsHost`), con velo, desenfoque desde Android 12 y la portada levantada. Tiene una acción de TalkBack "Más acciones".
  - **Terminado y no leído:** se guardan como posición (no leído es `{"unread":true}`) y viajan con la sincronización de posición.
  - **Portada propia:** la foto se elige con el Photo Picker y solo vale en este dispositivo.
  - **Quitar:** borra de la cuenta. `RemovalSync` pone `deletedAt` y borra los archivos de Storage, quita los borrados de otros dispositivos al sincronizar y, con la app a la vista, al llegar. Un libro abierto en el lector se quita al cerrarlo. Usa la tabla `removed_books`, de la base v7.
  - **Libros nuevos al instante:** `LibraryWatcher` escucha los libros cambiados en los últimos 15 minutos y pide una pasada si aparece uno desconocido.
  - Plan `specs/plans/2026-10-12-library-actions.md`.
- Perfil (PRF-001 a PRF-019, diseño 09, K-190 a K-194):
  - **Pantalla:** ruta `profile` de la barra (`ui/profile`). Lo que todavía no existe avisa "Llega más adelante.". Colores nuevos en `MarginColors`; íconos de Material Symbols en `ProfileIcons`.
  - **Espacio:** `QuotaSource` con `plan` y una caché en DataStore (`QuotaCacheImpl`) para mostrarlo sin conexión. Los tamaños usan la unidad del total.
  - **Ajustes de lectura:** los mismos de `SettingsRepository` que usa el lector. El panel de letra reutiliza `FontChips` y `SizeButtons` con una paleta clara.
  - **Cerrar sesión:** `SignOut` deja de escuchar la nube, espera la pasada en curso (`LibrarySync.whileIdle`), borra con `LocalDataWiper` (la base, los archivos y el DataStore, salvo los ajustes del lector y `sync_device_id`), cancela el recordatorio, sale de Firebase y de Credential Manager y vuelve a la bienvenida.
  - **Ayuda:** `domain/help/Faq.kt` con las respuestas en `strings.xml`.
  - **Solo con Wi-Fi (PRF-023, K-207):** `domain/sync/SyncPolicy.kt` (`canSync`, `SyncGate`, `NetworkSyncGate`). Red por defecto con `ConnectivityManager.registerDefaultNetworkCallback` (`ConnectivityNetworkMonitor`; Wi-Fi o cable cuentan), preferencia `sync_wifi_only` en DataStore (en `RETAINED_KEYS`). La puerta la consultan `SyncLibraryUseCase` (entre pasos), `PositionSync`, `RemovalSync`, `LibraryWatcher` y `DownloadBookUseCase`; `LibrarySync` muestra `SyncIssue.WaitingForWifi`. `SyncGateWatcher` (dentro de `CloudListeners`, solo con la app a la vista) pide una pasada y envía posiciones cuando la puerta se abre.
  - Plan `specs/plans/2026-10-12-profile.md`.

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en un dispositivo real, y beta cerrada aprobada en Google Play.
