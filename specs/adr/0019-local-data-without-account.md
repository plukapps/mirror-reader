# ADR 0019 — Datos locales sin cuenta y su paso a la cuenta

**Estado:** provisional. Depende de los ADR 0002, 0006, 0014 y 0015.

## Contexto

`13-no-account.md` (GST) permite usar la app sin cuenta, solo en el dispositivo, y pide que al crear la cuenta o iniciar sesión todo lo local pase a la cuenta (GST-012), sin borrar lo que se quitó sin cuenta de la nube (GST-020).

Cómo está hoy Android (revisado el 2026-10-15 en `develop`):

- Ninguna tabla de Room ni clave de DataStore lleva el uid. El dispositivo tiene una sola biblioteca: la de la sesión actual. Cerrar sesión la borra (`LocalDataWiper`, PRF-009).
- Lo que falta subir se marca en cada fila: libros con `uploadedAt IS NULL`, posiciones con `isSynced = false`, notas con `syncedAt == null`, libros quitados con `RemovedBookEntity(cloudDeleted = false)`.
- `LibrarySync` no hace nada sin sesión y arranca una pasada cuando aparece un usuario. Cada pieza de Firebase falla con `RemoteUnavailableException` sin sesión.
- El arranque (`startDestination`) solo mira si hay sesión.

## Opciones

1. **Columna de dueño (`ownerUid`) en cada tabla.** Las filas sin dueño se adoptan al iniciar sesión. Es explícito, pero exige una migración de Room en cuatro tablas y tocar cada consulta. Además, no compra nada: el dispositivo nunca tiene dos bibliotecas a la vez, porque cerrar sesión borra todo.
2. **Biblioteca única sin dueño, adoptada por la próxima sesión.** Se aprovecha que la subida ya está guiada por las marcas de pendiente de cada fila: lo hecho sin cuenta queda pendiente, y la primera pasada con sesión lo sube. Solo hay que cambiar lo que no debe viajar a la nube.
3. **Cuenta anónima de Firebase.** Descartada en el spec: nube a nombre de nadie, datos huérfanos y abuso de espacio.

## Decisión

Opción 2.

- **Sin columna de dueño.** Lo que se hace sin cuenta (libros, portadas, posiciones, notas) queda con sus marcas de pendiente, como lo hecho sin conexión. La primera pasada después de iniciar sesión lo sube a esa cuenta. La unión con la nube (GST-013) es la de siempre: el libro se identifica por su SHA-256, la posición por la lectura más reciente (SYN-003) y las notas por su id (SYN-004).
- **Quitar un libro sin sesión no deja un borrado pendiente para la nube** (GST-020). El libro se borra del dispositivo y no se escribe `RemovedBookEntity`. Si no, la primera pasada con sesión borraría ese libro de una cuenta que lo tuviera.
- **"Ya pasó por la bienvenida"** es una clave nueva de DataStore, de este dispositivo. Cerrar sesión la borra junto con el resto (no entra en `RETAINED_KEYS`), así que se vuelve a la bienvenida (GST-016). La marca de "aviso sin cuenta descartado" (GST-010) también se borra.
- **El arranque** pasa a `startDestination(user, welcomeSeen)`. Quien actualiza con sesión no depende de la clave nueva (GST-004).
- **Lo que necesita la nube se decide en la interfaz**, por "hay sesión": la línea de estado de sincronización, las hojas de cuota y de archivo muy grande, el aviso del 80 %, la sección de cuenta de Perfil. La lógica de sincronización no cambia: ya no hace nada sin sesión.
- **El plan sin sesión es Free** (`planOf(null)`, GST-007). No se escribe ninguna cuota local.

## Consecuencias

- Lo que se usa sin cuenta se sube a la primera cuenta que inicia sesión en el dispositivo, sin preguntar. Es lo que pide GST-012. Si alguien inicia sesión en una cuenta que no es la suya, sus libros locales van a esa cuenta.
- Cerrar sesión sigue borrando todo. Para conservar una biblioteca local al salir habría que volver a esta decisión, probablemente con la opción 1.
- Un libro quitado sin sesión que la cuenta tenía en la nube vuelve a aparecer al iniciar sesión, como "solo en la nube". Lo dice GST-020.
- iOS sigue el mismo modelo cuando se implemente. Hay que revisar que SwiftData tampoco guarde el uid.
