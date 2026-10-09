# AGENTS.md

Lector de EPUB multiplataforma (Android, iOS, Mac, web). El usuario sube sus propios libros; la app no trae contenido. Se publica en las tiendas de aplicaciones. Desarrollo en solitario, con apoyo de agentes.

## Estructura del repo

- `specs/` — fuente de verdad del producto (SDD). Empezar por `specs/README.md`.
  - `specs/product/` qué hace la app, `specs/platforms/` alcance por plataforma, `specs/adr/` decisiones técnicas.
  - `specs/plans/` planes de implementación, uno por rebanada.
- `code/android/` — app Android (Kotlin, Compose, Hilt, Room, DataStore, Readium). Stack y capas: `specs/adr/0005-android-stack-and-architecture.md`.
- `code/apple/` — app de Mac (e iOS más adelante) en Swift y SwiftUI. Stack y capas: `specs/adr/0010-apple-stack-and-architecture.md`.
- `design/` — diseños y maquetas.
- `KANBAN.md` — tablero de trabajo.

## Flujo de trabajo (SDD)

1. Spec primero. No se escribe código de una funcionalidad sin un requisito aprobado en `specs/`.
2. Plan después. Cada rebanada tiene un plan en `specs/plans/`, aprobado antes de implementar.
3. Código con tests. Cada requisito implementado tiene al menos un test que lo referencia por ID.
4. Si el código y el spec difieren, se corrige uno de los dos en el mismo cambio. Nunca se deja la divergencia.
5. Los specs de `specs/product/` no nombran tecnología. Eso vive en `specs/adr/`.
6. Una decisión técnica nueva o cambiada se registra como ADR.

## Skills preferidos

El proyecto incluye los skills de [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills) en `.agents/skills/` (enlazados desde `.claude/skills/`). **Se prefieren sobre cualquier otro skill equivalente** (por ejemplo los de superpowers). Si hay duda sobre cuál usar, empezar por `using-agent-skills`.

| Momento | Skill |
|---------|-------|
| Idea vaga o requisito ambiguo | `idea-refine`, `interview-me` |
| Escribir o cambiar un spec | `spec-driven-development` |
| Dividir un spec en tareas, escribir un plan | `planning-and-task-breakdown` |
| Implementar una tarea | `incremental-implementation` con `test-driven-development` |
| Usar una API o librería (Readium, Android, Firebase) | `source-driven-development`: verificar contra la documentación oficial, no de memoria |
| Contrato entre módulos o de sincronización | `api-and-interface-design` |
| Registrar una decisión técnica | `documentation-and-adrs` |
| Algo falla o se rompe | `debugging-and-error-recovery` |
| Revisar un cambio antes de integrarlo | `code-review-and-quality`, luego `code-simplification` si hace falta |
| Commits, ramas, versiones | `git-workflow-and-versioning` |
| Cuentas, datos de usuario, sincronización | `security-and-hardening` |
| Umbrales de calidad (cobertura, rendimiento) | `constraint-driven-development` |
| Decisiones de alto riesgo o irreversibles | `doubt-driven-development` |
| CI y publicación | `ci-cd-and-automation`, `shipping-and-launch` |
| Fase web | `frontend-ui-engineering`, `browser-testing-with-devtools` |

Si no existe skill del proyecto para la tarea, se puede usar otro. Las reglas de este archivo (specs en `specs/`, Kanban, español) mandan sobre lo que diga cualquier skill.

## Kanban (siempre)

Todo el trabajo se gestiona en `KANBAN.md`. Reglas:

- Columnas: Backlog, Listo, En curso, Revisión, Hecho.
- Una tarjeta por tarea del plan, con ID `K-NNN`.
- Máximo 1 tarjeta en "En curso" a la vez.
- Antes de empezar una tarea, mover su tarjeta a "En curso". Al terminar el código y los tests, a "Revisión". Cuando el usuario o la revisión la aprueba, a "Hecho".
- Trabajo nuevo o bugs encontrados: tarjeta nueva en Backlog, no se arreglan en silencio.
- Cada commit menciona la tarjeta, por ejemplo `K-003`.

## Convenciones

- Idioma: español para specs, planes, textos de interfaz y mensajes de commit. Identificadores de código en inglés.
- Requisitos con ID estable (`LIB-003`, `RDR-006`, ...). Código, tests y commits los referencian. No se reutilizan IDs.
- Commits pequeños y frecuentes. Formato `tipo: descripción` (`feat`, `fix`, `docs`, `test`, `chore`).
- Nunca guardar en el repo libros de terceros ni credenciales. Los EPUB de muestra van en `code/android/samples/` (ignorada por git).

## Android

Arquitectura MVVM en capas, un solo módulo Gradle, paquetes `com.pluk.reader.{ui,domain,data,di}`:

- `ui`: pantallas Compose, `ViewModel` (un `StateFlow<UiState>` por pantalla), navegación (Navigation Compose, una sola actividad), tema.
- `domain`: modelos propios, interfaces de repositorio y casos de uso con lógica. Sin Android ni Compose.
- `data`: repositorios, Room, DataStore Preferences, motor de EPUB (Readium) y, desde la rebanada de sync, el SDK de Firebase (Auth, Firestore, Storage) detrás de interfaces de `domain`. Sin Retrofit.
- `di`: módulos de Hilt (con KSP, no kapt).
- Coroutines y Flow para lo asíncrono. Sin `SharedPreferences`. Sin dependencias sin uso.

- Raíz del proyecto Gradle: `code/android`. Namespace y applicationId `com.pluk.reader`. `minSdk 26`.
- Motor de EPUB: Readium Kotlin Toolkit 3.4.0 (ADR 0004, provisional).
- Tests JVM: `./gradlew :app:testDebugUnitTest`
- Tests en emulador: `./gradlew :app:connectedDebugAndroidTest` (requiere un emulador encendido; listar con `~/Library/Android/sdk/emulator/emulator -list-avds`).
- Un solo test o clase en emulador (AGP 9 no acepta `--tests`): `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pluk.reader.reader.NombreTest`.
- Los tests que tocan clases de Readium (`EpubPreferences`, `Locator`), Room, DataStore o la pantalla del lector corren en emulador, no en JVM. Los de `ReaderViewModel` y la lógica pura corren en JVM con repositorios falsos.
- Los tests de emulador usan Hilt: `HiltTestRunner` y `TestStorageModule` (Room en memoria y DataStore temporal reemplazan a `StorageModule`). En tests con Compose, los bucles de espera deben llamar `compose.waitForIdle()`.
- Toolchain: AGP 9.1.0, Gradle 9.3.1, `compileSdk 37` (lo exige Readium 3.4.0) y core library desugaring. La API de Readium se verifica con `javap` sobre `~/.gradle/caches/.../readium-*-api.jar` cuando la documentación no alcanza.
- Si hay más de un dispositivo conectado, `connectedDebugAndroidTest` corre en todos (también en un teléfono físico). Para limitarlo al emulador: `ANDROID_SERIAL=emulator-5554 ./gradlew ...`.
- Fixture de pruebas: `code/android/tools/make_fixture_epub.py`.
- Pruebas con adb: los enlaces web del libro abren el navegador al tocarlos. En builds de depuración se bloquean con `adb shell run-as com.pluk.reader touch files/block_external_links` y se vuelven a permitir con `rm files/block_external_links`.
- Firebase (ADR 0005, ADR 0007): `code/android/app/google-services.json` se descarga de la consola (Configuración del proyecto → app `com.pluk.reader`) y NO va al repo (está en `.gitignore`). Sin él, el build falla. Revisar `git status` antes de commitear: nunca `git add -A` a ciegas.

## Apple (Mac e iOS)

Raíz: `code/apple`. Detalle y comandos: `code/apple/README.md`. Plataforma: `specs/platforms/apple.md` (borrador).

- Requisitos: Xcode 16.2 (el último para macOS 14 de esta máquina) con `xcode-select` apuntando a `/Applications/Xcode.app`, y XcodeGen (`brew install xcodegen`).
- El proyecto se describe en `project.yml`; `xcodegen` genera `Reader.xcodeproj` (no va al repo). Regenerar al agregar archivos o cambiar `project.yml`.
- Capas: dominio en el paquete `Packages/ReaderDomain` (sin SwiftUI), UI en `App/UI`, datos en `App/Data`. `ViewModel` con `@MainActor @Observable`, repositorios por inicializador.
- Tests con Swift Testing: dominio con `swift test` (desde `Packages/ReaderDomain`); app con `xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=macOS' -derivedDataPath .build/xcode test` (en iPhone: `-destination 'platform=iOS Simulator,name=iPhone 16'`). Hay tests que solo corren en una de las dos; correr ambos destinos.
- Un solo target `Reader` con destinos Mac e iOS (`supportedDestinations`); lo que es solo de Mac va en `project.yml` con `[sdk=macosx*]`.
- Firma local, sin cuenta de Apple Developer. Sandbox activado.

## Backend (Firebase)

Raíz: `code/backend/v1` (reglas de Firestore y Storage, Cloud Functions en `functions/`, tests en `tests/`). Detalle y pasos manuales: `code/backend/v1/README.md`. Modelo de datos: `specs/platforms/backend.md`.

- Tests (emuladores de Auth, Firestore, Storage y Functions): desde `code/backend/v1`, `npm test`. Exige JDK 21 o superior: en esta máquina, `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` y su `bin` en el `PATH`.
- Los tests usan el proyecto `demo-pluk-reader`; nunca tocan el proyecto real (`mirror-reading-staging`: Firestore en `us-central1`, bucket de Storage en `us-east1`).
- Desplegar al proyecto real es una acción aparte que se pide al usuario.

## Decisiones vigentes

- Clientes nativos por plataforma, Android primero (ADR 0001).
- Mac e iOS en un solo proyecto SwiftUI en `code/apple`, generado con XcodeGen, provisional (ADR 0010).
- Offline-first (ADR 0002).
- Backend Firebase, provisional (ADR 0007, reemplaza al 0003). Código en `code/backend/v1`.
- Tipografías del lector: Newsreader, Host Grotesk y JetBrains Mono en `assets/fonts`, provisional (ADR 0009). Los controles del lector (barra superior y panel de ajustes) siguen `design/Margin Ebook App.dc.html`, pantallas 06 a 08.
- Modelo de negocio abierto, hipótesis: suscripción. Ver `specs/open-questions.md`.
