# Apple — Inicio con los libros reales del usuario

**Objetivo:** que Inicio en iPhone (y en la Mac, mismo target) muestre los libros que el usuario tiene en su cuenta, con título, autor y portada, guardados en una base local como en Android. Hoy muestra una biblioteca falsa (`FakeLibraryRepository`).

**Spec:** `specs/product/07-home.md` (HOM-002, HOM-003, HOM-008 a HOM-010), `specs/product/01-library.md` (LIB-003, LIB-007, LIB-012), `specs/product/04-sync.md` (SYN-001 y SYN-002, solo la bajada al abrir), `specs/platforms/apple.md`. Modelo de datos: `specs/platforms/backend.md`. Decisión técnica: ADR 0012.

**Rama:** `feature/ios-home-data`, desde `master`.

## Fuera de alcance

- Importar EPUB, subir libros y portadas, descargar archivos EPUB (sin lector todavía, no hay para qué bajarlos).
- Escribir la posición: sin lector en Apple no hay posición propia que enviar. Solo se baja la de la nube (ADR 0011) para que Inicio muestre el progreso.
- Sincronización al volver a la app con TTL (K-083 en Android). Acá se sincroniza una vez por arranque.
- Pantallas de cuenta: se usa la cuenta de desarrollo, como Android (K-052).

## Decisiones

- Capas como Android (ADR 0005, 0010): el dominio define `RemoteLibrary`, `CoverStore`, `CloudBooksRepository` y `AccountRepository`, y el caso de uso `LibrarySync`. La capa de datos los implementa con SwiftData y Firebase.
- `LibrarySync` (dominio): inicia sesión con la cuenta de desarrollo si está configurada, lista la nube, agrega los que faltan como "solo en la nube", baja las posiciones de lectura (SYN-002: progreso y momento de la última lectura, gana la más reciente por `readAt`) y las portadas que faltan. Sin conexión o sin sesión no cambia nada (ADR 0002). Un fallo de portada no corta las demás.
- Inicio pinta primero lo local y vuelve a leer la base cuando termina la sincronización.
- Fecha de alta de un libro que llega de la nube: `createdAt` del documento (cuándo se subió), o la hora actual si falta. Android usa la hora actual; con la de la nube el orden de "Agregados recientemente" es el mismo en todos los dispositivos. Tarjeta aparte para alinear Android.

## Tareas

- **K-100** Docs: ADR 0012, este plan, `apple.md`, Kanban.
- **K-101** Dominio: modelos y protocolos remotos, `LibrarySync`. Tests con `swift test` y repositorios falsos (LIB-007, LIB-012, SYN-001).
- **K-102** Base local con SwiftData: `BookRecord`, `PositionRecord`, `LibraryStore` (biblioteca y libros de la nube), portadas en `Application Support/covers`. Tests en el simulador con la base en memoria.
- **K-103** Firebase: SDK en `project.yml`, app de iOS registrada, `GoogleService-Info.plist` fuera del repo, implementaciones de Auth, Firestore (libros y posiciones) y Storage (portadas), credenciales de la cuenta de desarrollo (script que las copia de `local.properties`).
- **K-104** Inicio con datos reales: `ReaderApp` arma el grafo, `HomeViewModel` carga lo local, sincroniza y recarga. Tests del ViewModel. Verificación a mano en el simulador de iPhone contra el proyecto real y build de la Mac.

## Verificación

- `swift test` en `Packages/ReaderDomain`.
- `xcodebuild test` en el simulador de iPhone y en la Mac.
- A mano: el simulador muestra los libros de la cuenta de desarrollo con sus portadas y el progreso que dejó el teléfono; al reabrir sin red siguen ahí.

## Resultado

- Verificado en el simulador (iPhone 16 Pro) contra `mirror-reading-staging` con la cuenta de desarrollo: 25 libros, 9 posiciones y 23 portadas. "Continuar leyendo" muestra el libro que se leyó último en el teléfono, con su progreso. Al reabrir, todo sale de la base local al instante.
- En la primera pasada los libros aparecen a los ~5 s con portadas generadas y las reales llegan después: `LibrarySync.run(onChange:)` avisa a Inicio tras guardar libros y posiciones, y otra vez tras las portadas. Antes Inicio esperaba ~20 s a que bajaran todas.
- Tests: 26 del dominio (`swift test`, 15 nuevos) y 19 de la app en el simulador (11 nuevos), todos pasan. Verificado por mutación: guarda de sesión y sincronización una sola vez por arranque.
- Los tests corren dentro de la app; `AppGraph` no configura Firebase bajo tests, así que nunca tocan el proyecto real.
- La Mac compila con sandbox y permiso de red. No se probó a mano: Firebase Auth en la Mac puede necesitar el llavero con firma de equipo (ADR 0012).
- Libros en "Leyendo" con 0 %: tienen una posición guardada al inicio del libro. Es la misma regla que Android (LIB-010: con posición es "leyendo").
- SDK de Firebase fijo en 12.14.0: desde la 12.15 exige Xcode 16.3 y esta máquina tiene 16.2 (K-080).
