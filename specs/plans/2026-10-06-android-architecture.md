# Android — Adaptación al stack y la arquitectura (ADR 0005)

**Objetivo:** pasar el visor de EPUB actual (actividades, `SharedPreferences`, sin DI) al stack definido en ADR 0005, sin cambiar lo que hace la app: mismos requisitos RDR-001 a RDR-007, LIB-002, AND-003.

**Spec que implementa:** `specs/adr/0005-android-stack-and-architecture.md`, `specs/platforms/android.md` (AND-005).

**Enfoque:** cuatro rebanadas, cada una deja la app compilando y los tests en verde. Se migra por dentro y la app visible no cambia, salvo que el lector deja de ser una actividad.

## Restricciones globales

- Paquetes `com.pluk.reader.{ui,domain,data,di}` en un solo módulo.
- `domain` no importa nada de `android.*` ni de Compose.
- Hilt con KSP (no kapt). Sin `SharedPreferences`.
- Retrofit, OkHttp y Gson no se agregan todavía (rebanada de sync, K-010).
- Versiones verificadas en Maven: Hilt 2.60.1, Room 2.8.5, DataStore 1.2.1, Navigation Compose 2.10.2, Hilt Navigation Compose 1.4.0, fragment-compose 1.9.1, KSP 2.3.x.
- Código y tests referencian el ID del requisito.

## Estructura destino

```
com.pluk.reader/
├── ReaderApp.kt                      @HiltAndroidApp
├── MainActivity.kt                   FragmentActivity, NavHost, VIEW intent
├── ui/
│   ├── navigation/AppNavHost.kt      rutas home y reader
│   ├── home/HomeScreen.kt
│   ├── reader/ReaderScreen.kt        Compose + AndroidFragment (Readium)
│   ├── reader/ReaderViewModel.kt     un StateFlow<ReaderUiState>
│   ├── reader/ReaderUiState.kt
│   ├── reader/ReaderControls.kt      barra y diálogo de índice
│   └── theme/
├── domain/
│   ├── model/    ReaderSettings, ReaderTheme, OpenedBook, TocEntry
│   ├── repository/  SettingsRepository, PositionRepository
│   ├── usecase/  OpenBookUseCase
│   └── Progress.kt
├── data/
│   ├── epub/PublicationLoader.kt
│   ├── local/db/  ReaderDatabase, ReadingPositionEntity, ReadingPositionDao
│   ├── local/prefs/  ReaderSettingsDataStore
│   └── repository/  SettingsRepositoryImpl, PositionRepositoryImpl
└── di/  StorageModule, RepositoryModule, EpubFragmentModule
```

## Tareas

### Tarea 1 — K-014: Hilt, esqueleto de capas y navegación de una sola actividad (M)

**Criterios de aceptación**
- [ ] Plugins y dependencias de Hilt (KSP), Navigation Compose y Hilt Navigation Compose compilan con AGP 9.1.
- [ ] `ReaderApp` con `@HiltAndroidApp`. `MainActivity` es `@AndroidEntryPoint`, hereda de `FragmentActivity` y aloja un `NavHost` con la ruta `home`. La ruta `reader` llega en la Tarea 3; mientras tanto `home` sigue abriendo `ReaderActivity`.
- [ ] Los paquetes de las cuatro capas existen y el código actual está movido a ellos (`ReaderSettings` y progreso a `domain` —el enum de tema se renombró `ReadingTheme` para no chocar con el tema Compose `ReaderTheme`—, `PublicationLoader` a `data/epub`, tema a `ui/theme`).
- [ ] La app sigue funcionando: `home` abre el selector y navega a `reader`.

**Verificación:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`; los tests existentes se adaptan a los nuevos paquetes.
**Archivos:** `build.gradle.kts`, `libs.versions.toml`, `ReaderApp.kt`, `MainActivity.kt`, `ui/navigation/*`, `ui/home/*`, movimientos de paquete.

### Tarea 2 — K-015: capa de datos con Room y DataStore (M)

**Criterios de aceptación**
- [ ] Room guarda la posición por libro (`bookId`, locator serializado, fecha). Esquema exportado a `schemas/`.
- [ ] DataStore Preferences guarda modo, tema y tamaño. La escala se guarda en décimas enteras (RDR-002).
- [ ] `SettingsRepository` expone `Flow<ReaderSettings>` y `update`. `PositionRepository` expone `get` y `save`.
- [ ] Un dato de posición dañado no rompe la app (RDR-006).

**Verificación (emulador):** test del DAO con base en memoria, test del repositorio de ajustes con un DataStore temporal, test de round-trip de posición.
**Archivos:** `data/local/**`, `data/repository/**`, `domain/repository/**`, `di/StorageModule.kt`, `di/RepositoryModule.kt`.

### Tarea 3 — K-016: pantalla del lector en Compose (L, la más delicada)

**Criterios de aceptación**
- [ ] `ReaderScreen` reemplaza a `ReaderActivity`. El navegador de Readium se aloja con `AndroidFragment`.
- [ ] `ReaderViewModel` (Hilt) abre el libro desde un argumento de navegación guardado en `SavedStateHandle` y expone un único `StateFlow<ReaderUiState>` con carga, error, libro listo, índice, ajustes, progreso y visibilidad de controles.
- [ ] Posición restaurada al abrir y guardada al leer (con `debounce`). Ajustes aplicados al navegador.
- [ ] Un EPUB inválido muestra el error (LIB-002) sin cerrar la app.
- [ ] Rotación o recreación: el lector sigue visible. Si el proceso se perdió, vuelve a `home`.
- [ ] Enlaces externos solo `http` y `https`, abiertos con el navegador del sistema.
- [ ] Un EPUB abierto con "Abrir con" (VIEW) llega a `reader`.

**Verificación:** tests JVM del `ReaderViewModel` con repositorios falsos (error, ajustes, progreso) y tests de emulador de la pantalla.
**Archivos:** `ui/reader/*`, `di/EpubFragmentModule.kt`, `MainActivity.kt`, `domain/usecase/OpenBookUseCase.kt`, `domain/model/*`.

### Tarea 4 — K-017: pruebas con Hilt, limpieza y docs (M)

**Criterios de aceptación**
- [ ] Runner de pruebas con `HiltTestApplication`. Módulos de prueba reemplazan Room (en memoria) y DataStore (archivo temporal).
- [ ] Los tests instrumentados de lector, TOC, posición y ajustes migrados y verdes.
- [ ] Sin `ReaderActivity`, `SharedPreferences` ni dependencias sin uso (se evalúa el plugin de serialization y Navigation 3 de la plantilla).
- [ ] `AGENTS.md`, `KANBAN.md` y este plan actualizados.

**Verificación:** `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` completo, y captura de pantalla en el emulador.

## Checkpoints

- Después de la tarea 1: la app compila, abre un libro como antes.
- Después de la tarea 3: ya no existe `ReaderActivity`. Revisar con el dueño del producto antes de seguir.
- Después de la tarea 4: todo en verde.

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Hilt y KSP con el Kotlin integrado de AGP 9 | Alto | Verificar en la tarea 1 antes de tocar nada más. |
| El `Fragment` de Readium dentro de Compose y su restauración de estado | Alto | Proveedor de fábrica inyectado y descartar el estado si el proceso se perdió. Pruebas de recreación. |
| Las pruebas instrumentadas dependen de singletons reales | Medio | Hilt de pruebas con módulos de reemplazo. |
| La plantilla trae Navigation 3 y serialization sin uso | Bajo | Se eliminan en la tarea 4 si nada los usa. |
