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
- Arranque y bienvenida (WEL-001 a WEL-008): el arranque sale del tema, sin `core-splashscreen`. En Android 12 o superior lo arman `windowSplashScreenBackground` y `windowSplashScreenAnimatedIcon`; en Android 8 a 11, `windowBackground` con un `layer-list`. Nada lo retiene. `MainViewModel.start` decide una vez por arranque entre la ruta `welcome` e Inicio, con la marca guardada en DataStore y la sesión que ya conoce Firebase. Plan `specs/plans/2026-10-09-android-welcome.md`.

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en un dispositivo real, y beta cerrada aprobada en Google Play.
