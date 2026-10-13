# Plan — Orden de la biblioteca por entrada a la cuenta (Android e iOS)

**Objetivo:** que la biblioteca (y la fila "Agregados recientemente" de Inicio) muestre primero el último libro que entró a la biblioteca del usuario, con el mismo orden en todos sus dispositivos.

**Specs que implementa:** LIB-021 (`specs/product/01-library.md`, nuevo). Afecta a HOM-009, que ordena por la misma fecha.
**Rama:** `feature/library-order` (worktree `.claude/worktrees/book-order-android-ios-6d6c3a`), desde `develop`. **PR:** uno solo contra `develop`, con las dos plataformas. **Tarjetas:** K-210 a K-213; K-214 a K-216 (volver a importar).
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
- **Quitar y volver a importar** (LIB-020): ver "Segunda parte". Al principio se aceptó que el libro volviera a su lugar original; el usuario lo probó y pidió que cuente como entrada nueva (2026-10-13).
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

## Segunda parte: volver a importar (K-214 a K-216)

**Problema:** al quitar un libro el documento de la nube solo se marca con `deletedAt`. Al volver a importarlo, `saveBook` lo revive sin tocar `createdAt` (la regla lo prohíbe), y la pasada siguiente le devuelve la fecha vieja. Lo mismo pasa si se vuelve a importar antes de que el borrado llegue a la nube: el documento nunca se marcó.

**Decisión (aprobada por el usuario, 2026-10-13):** campo nuevo `addedAt` en `users/{uid}/books/{bookId}`.
- Hora del servidor. Opcional: los documentos viejos y las versiones de la app ya instaladas no lo mandan.
- Cada subida de un libro importado en el dispositivo (`saveBook`) lo escribe, también al revivir. Si importo en un dispositivo un libro que ya está en mi biblioteca no se sube (la app avisa que ya existe), así que no hay renovaciones de más.
- La fecha de entrada es `addedAt` y, si falta, `createdAt`. `createdAt` sigue sin cambiar.
- Se descartó renovar `createdAt` al revivir: dejaría de significar "cuándo se creó el documento".
- **Orden de despliegue:** primero las reglas, después las apps. Con las reglas de hoy un `saveBook` con `addedAt` falla y no se sube nada. Desplegar a `mirror-reading-staging` se pide al usuario.

### K-214 — Spec y backend
- LIB-021: volver a importar cuenta como entrada nueva, con escenario. `backend.md`: campo `addedAt`.
- `firestore.rules`: `addedAt` en `hasOnly`; al estar presente, hora del servidor o el valor que ya tenía.
- Tests de reglas (emuladores): crear con y sin `addedAt`, renovarlo al revivir, rechazar una fecha del cliente, editar el título conservándolo.

### K-215 — Android
- `RemoteBook.createdAt` pasa a ser la fecha de entrada: `FirestoreRemoteLibrary.listBooks` lee `addedAt ?: createdAt`. `saveBook` escribe `addedAt` (servidor) al crear y al actualizar.
- Test JVM con LIB-021 de que un libro conocido toma la fecha nueva más reciente (ya cubierto por `cloudAddedAt`, se agrega el caso de "vuelve a entrar").

### K-216 — iOS
- Igual: `listBooks` lee `addedAt ?? createdAt`; `saveBook` escribe `addedAt` al crear y al actualizar.
- Test en `LibraryStoreTests`: un libro que vuelve a entrar con fecha más nueva sube al primer lugar.

## Verificación

- `./gradlew :app:testDebugUnitTest` y `:app:compileDebugAndroidTestKotlin`.
- `swift test` en `Packages/ReaderDomain` y `xcodebuild test` en iPhone 16.
- A mano: importar un libro en cada plataforma y ver el mismo orden en las dos.

## Resultado (2026-10-13)

- Android: 414 tests JVM pasan (4 nuevos en `CloudAddedAtTest`). `CloudBooksOrderTest` (2 casos, emulador) compila, sin correr.
- iOS: dominio 205 tests, iPhone 188 (2 nuevos en `LibraryStoreTests`). Mac compila; sus tests no arrancan (K-201).
- Docs de plataforma: sin cambios, no describían el orden.
- Falta la verificación a mano: el teléfono y el iPhone con la misma cuenta deben mostrar el mismo orden después de sincronizar.
