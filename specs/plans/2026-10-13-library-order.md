# Plan — Orden de la biblioteca por entrada a la cuenta (Android e iOS)

**Objetivo:** que la biblioteca (y la fila "Agregados recientemente" de Inicio) muestre primero el último libro que entró a la biblioteca del usuario, con el mismo orden en todos sus dispositivos.

**Specs que implementa:** LIB-021 (`specs/product/01-library.md`, nuevo). Afecta a HOM-009, que ordena por la misma fecha.
**Rama:** `feature/library-order` (worktree `.claude/worktrees/book-order-android-ios-6d6c3a`), desde `develop`. **PR:** uno solo contra `develop`, con las dos plataformas. **Tarjetas:** K-210 a K-213.
**Pedido del usuario (2026-10-13):** opción 1, "el último que se agregó a la biblioteca primero", entendido como el momento en que el libro entró a la cuenta.

## Situación actual

Las dos plataformas ordenan por `addedAt` descendente (`BookDao.observeAll`, `LibraryStore.books()`). La diferencia está en el `addedAt` de los libros que llegan de la nube:

- **Android** (`CloudBooksRepositoryImpl.addCloudOnly`): `now - index`, la hora en que el dispositivo los vio. `RemoteBook` no trae `createdAt` y la consulta no tiene orden, así que Firestore los devuelve por id (el hash). Resultado: todos los libros de la nube quedan arriba de los locales y en un orden arbitrario.
- **iOS** (`LibraryStore.addCloudOnly`): `createdAt ?? seenAt` al insertar. Un libro que ya estaba en la base (importado aquí) conserva su hora de importación local.

## Decisiones

- **Fecha de entrada = `createdAt` del documento en la nube** (hora del servidor, no cambia, `backend.md`). Mientras el libro no está en la nube, la hora de importación local.
- **La nube manda también para los libros que ya están en la base.** En cada listado de la nube, si el `createdAt` de un libro difiere del `addedAt` local, se reemplaza. Así:
  - los libros que Android ya bajó con la fecha equivocada se corrigen solos en la próxima pasada, sin migración;
  - un libro importado aquí toma la fecha de la nube después de subirse. Normalmente son segundos de diferencia; si se importó sin conexión, sube de lugar al subirse (lo dice LIB-021).
- **Sin cambios en el backend.** Se descartó que la nube guarde la hora de importación del cliente: exige cambiar las reglas y `saveBook` en las dos plataformas, y la diferencia solo se nota en importaciones sin conexión.
- **Quitar y volver a importar** (LIB-020): el documento revive con su `createdAt` original (la regla no deja cambiarlo), así que el libro vuelve primero en este dispositivo y, tras la pasada, a su lugar original. Se acepta por ahora y queda en Backlog (K-214) por si se quiere que cuente como entrada nueva.
- Si un documento viejo no trae `createdAt`, se conserva lo que haya: la hora local de importación o la hora en que se vio.

## Tareas

### K-210 — Spec y plan
- LIB-021 y sus dos escenarios en `01-library.md`. Este plan. Tarjetas en `KANBAN.md`.

### K-211 — Android
- `RemoteBook` gana `createdAt: Long?` (milisegundos). `FirestoreRemoteLibrary.listBooks` lo lee del `Timestamp`. `toRemoteBook` (subida) lo deja en null.
- Lógica pura en `domain` (JVM): dada la lista de la nube, los `addedAt` locales conocidos y `now`, decide el `addedAt` de cada libro nuevo (`createdAt ?: now - index`) y qué libros existentes cambian de fecha.
- `CloudBooksRepositoryImpl.addCloudOnly` usa esa lógica: inserta los nuevos y actualiza los existentes con `BookDao.updateAddedAt(id, addedAt)`. Respeta los libros quitados (LIB-020) como hoy.
- Tests: JVM de la lógica pura con el ID LIB-021 (orden con locales y de la nube mezclados, corrección de un libro ya bajado, sin `createdAt`). Caso en `BookEditorTest`/test de emulador del repositorio: se compila y no se corre sin preguntar.

### K-212 — iOS
- `LibraryStore.addCloudOnly`: además de insertar, si un registro existente tiene otro `addedAt` y la nube trae `createdAt`, lo reemplaza.
- Tests en `LibraryStoreTests` con el ID LIB-021: libro importado aquí toma el `createdAt`; mezcla de locales y de la nube; sin `createdAt` no cambia nada. El test "LIB-003: agregar dos veces no lo cambia" se ajusta: el título no cambia, la fecha sí puede.
- Correr dominio (`swift test`) y app en iPhone; Mac según K-201.

### K-213 — Cierre
- Notas en `specs/platforms/android.md` y `apple.md` si corresponde. Verificación a mano: teléfono y iPhone con la misma cuenta muestran el mismo orden.
- PR único contra `develop`.

## Verificación

- `./gradlew :app:testDebugUnitTest` y `:app:compileDebugAndroidTestKotlin`.
- `swift test` en `Packages/ReaderDomain` y `xcodebuild test` en iPhone 16.
- A mano: importar un libro en cada plataforma y ver el mismo orden en las dos.
