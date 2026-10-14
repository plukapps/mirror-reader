# Plan — Sincronización de notas con interruptor remoto (backend y Android)

**Objetivo:** que las notas lleguen a todos los dispositivos de la cuenta y que esa sincronización se pueda prender o apagar desde Remote Config, para todos o por plan, sin publicar una versión nueva.

**Specs que implementa:** SYN-016, SYN-017 (nuevos), SYN-005 (cambiado: gana el último cambio), ACC-011 (nuevo), PRF-009 (avisa también por notas sin subir), ANN-007 para notas. **Decisión técnica:** ADR 0016 (Remote Config). Modelo de datos: `specs/platforms/backend.md`.
**Rama:** `feature/notes-sync`, desde `claude/notes-functionality-f6f30e`. PR aparte, apilado sobre el de notas (plukapps/mirror-reader#37); pasa a `develop` cuando ese se integre.
**Aprobado por el usuario (2026-10-14).** **Tarjetas:** K-270 a K-277.
**Pedido del usuario (2026-10-14):** Remote Config; prendida para todos por ahora; gana el último cambio; apagada, las notas siguen locales y se suben al prenderla.

## Fuera de esta rebanada

- iOS: todavía no tiene notas. Cuando las tenga, usa el mismo modelo, las mismas reglas y el mismo parámetro.
- Que el servidor haga cumplir el flag (ADR 0016): llega con el cobro.
- Marcadores, subrayados sin nota y preferencias (resto de SYN-002).
- Indicador de estado por nota (SYN-008): la línea de estado de la Biblioteca no cambia.

## Decisiones

### Nube: `users/{uid}/notes/{noteId}`

| Campo | Tipo | Notas |
|---|---|---|
| `bookId` | string | SHA-256 del libro. El libro debe existir en `books` (acota la cantidad, como las posiciones). No cambia. |
| `locatorJson` | string | 1 a 8192 caracteres. No cambia. |
| `quote` | string | 1 a 4000 caracteres. No cambia. |
| `text` | string | 0 a 10 000 caracteres. Vacío solo en una nota borrada. |
| `progression` | número | 0 a 1. No cambia. |
| `createdAt` | int | Milisegundos, reloj del dispositivo que la creó. No cambia. |
| `editedAt` | int | Milisegundos, reloj del dispositivo en la última edición o borrado. **Decide quién gana** (SYN-005). |
| `deletedAt` | int o null | Marca de borrado (SYN-007), reloj del dispositivo. |
| `updatedAt` | timestamp | Hora del servidor. Solo para pedir lo que cambió. |

- `noteId` es el UUID local (36 caracteres).
- **Gana la edición más reciente, no la que llegó último** (como `readAt` en ADR 0011). La regla rechaza:
  - un `editedAt` menor que el guardado;
  - uno más de una hora en el futuro;
  - cambiar los campos que no cambian;
  - campos de más o de menos;
  - el borrado físico.
- Una nota borrada revive solo con una edición posterior al borrado: también entre borrar y editar gana el último cambio (SYN-005, SYN-010). Si no, una edición hecha sin conexión después de un borrado en otro dispositivo quedaría pendiente para siempre. Cambio hecho en K-273.

### Android: base local

- Room v9 (`MIGRATION_8_9`). `notes` suma:
  - `deletedAt INTEGER NULL`;
  - `isSynced INTEGER NOT NULL DEFAULT 0`;
  - `editedAt` (hoy `updatedAt`, se renombra en el modelo).
- Borrar una nota pasa a marcarla (`deletedAt`) y dejarla pendiente. Las pantallas no ven las borradas.
- Una nota borrada y ya subida se elimina de la base en la siguiente pasada.
- Con el flag apagado se conservan, para subirlas al prender (SYN-017).
- Quitar un libro (ANN-011) marca sus notas como borradas en lugar de eliminarlas, así el borrado llega a los otros dispositivos. El documento del libro sigue existiendo con su `deletedAt`, así que la regla las acepta. Si el libro nunca se subió, se eliminan como hoy.

### Android: el interruptor

- `FeatureFlags` en `domain/flags` con `notesSync: StateFlow<Boolean>`.
- `RemoteConfigFeatureFlags` en `data/flags`:
  - defaults en la app: `notes_sync_enabled = true`;
  - señal personalizada `plan` (de `StorageQuota.plan`, que ya trae `FirestoreQuotaSource`);
  - al arrancar, `activate` y después `fetchAndActivate`;
  - `addOnConfigUpdateListener` mientras la app está a la vista (dentro de `CloudListeners`).
- API verificada en la documentación oficial antes de escribir el código (source-driven).
- Dependencia nueva: `firebase-config` por el BoM 35.
- Los tests usan `FakeFeatureFlags`.

### Android: sincronizar

- `NoteSync` (dominio) detrás de `RemoteNotes` (data: `FirestoreNotes`):
  - **Subir:** las pendientes cuyo libro ya está en la nube. Al terminar, `markSynced(id, editedAt)`. Si hubo otra edición en el medio, sigue pendiente, como las posiciones.
  - **Bajar:** las que cambiaron desde el último `updatedAt` visto, cursor guardado en DataStore por cuenta. Se aplican solo si su `editedAt` es mayor que el local; una nota borrada en la nube se marca o se elimina acá.
  - **Lógica pura en JVM:** `mergeRemoteNote(local, remote)` devuelve qué hacer.
- Cuándo corre:
  - un paso más de `SyncLibraryUseCase`, después de las posiciones (SYN-001);
  - un listener de `notes` con la app a la vista (`CloudListeners`, SYN-016 "en segundos");
  - un envío al cerrar el lector si hay notas pendientes.
- Siempre detrás de `SyncGate` (solo Wi-Fi, PRF-023) **y** del flag.
- **Flag apagado** (SYN-017): no se sube, no se baja y el listener se detiene. Al prenderse, `NoteSync` corre una pasada completa.
- El lector y el detalle ya observan `NoteRepository`, así que una nota que llega se ve sola: subrayada y en la lista.

### Cerrar sesión (PRF-009)

`SignOut.hasPendingChanges` suma las notas pendientes. Con el flag apagado, cualquier nota local cuenta como pendiente. El texto del aviso pasa a "Hay libros, progreso o notas que todavía no se subieron…".

## Tareas

### K-270 — Spec, ADR y plan
- SYN-005 cambiado, SYN-016, SYN-017 y ACC-011 nuevos, PRF-009 y ANN-007. ADR 0016. Este plan. Tarjetas.

### K-271 — Backend: reglas de `notes` y tests
- `firestore.rules`, `backend.md` (colección nueva, quitar "anotaciones" de "Fuera de este modelo").
- Tests con emuladores (SYN-016, SYN-005, SYN-007):
  - crear;
  - editar más nuevo;
  - rechazar más viejo y más de una hora en el futuro;
  - campos que no cambian;
  - borrar con marca;
  - revivir solo con una edición posterior;
  - libro inexistente;
  - otro usuario;
  - sin borrado físico.
- Deploy a `mirror-reading-staging`: se pide al usuario. **Va antes que la app**: sin la regla, las subidas se rechazan y quedan pendientes, sin perder nada.

### K-272 — Android: interruptor con Remote Config
- `FeatureFlags`, `RemoteConfigFeatureFlags`, la señal `plan`, el listener y DI.
- Tests JVM del consumo con el doble (SYN-017): por defecto prendido; apagar detiene; prender dispara una pasada.

### K-273 — Android: base local con marcas de borrado
- Room v9 y `MIGRATION_8_9`. Repositorio:
  - `delete` marca;
  - `observe` filtra las borradas;
  - `pending()`, `markSynced`, `applyRemote`, `purgeSyncedDeletions`.
- Quitar un libro marca sus notas.
- Tests de emulador compilados (`MigrationTest` 8→9 y repositorio). Los JVM existentes, ajustados.

### K-274 — Android: subir y bajar notas
- `RemoteNotes`, `FirestoreNotes`, `NoteSync` y `mergeRemoteNote`.
- Paso en `SyncLibraryUseCase`, listener en `CloudListeners`, envío al cerrar el lector.
- Tests JVM con `FakeRemoteNotes`:
  - última edición gana (SYN-005);
  - borrado que se propaga (SYN-007);
  - pendiente que no se pisa;
  - libro que no está en la nube no se sube;
  - con el gate cerrado o el flag apagado no hay red (SYN-017);
  - el cursor avanza.

### K-275 — Android: aviso al cerrar sesión
- `SignOut.hasPendingChanges` con las notas y texto nuevo.
- Tests JVM en `SignOutTest` (PRF-009).

### K-276 — Remote Config y deploy (acciones sobre el proyecto real)
- El parámetro va en `code/backend/v1/remoteconfig.template.json`, el template versionado de la rama del catálogo de planes (ADR 0016, "Coordinación"). Si esa rama todavía no se integró, se crea el parámetro en la consola y se agrega al template en cuanto exista. Un deploy del template sin el parámetro lo borra.
- Se pide al usuario que cree en la consola el parámetro `notes_sync_enabled` (booleano, por defecto `true`), o que lo apruebe para hacerlo con la herramienta de Firebase.
- También se le pide el deploy de las reglas (K-271).
- Probar el apagado con una condición por instalación.

### K-277 — Cierre
- `android.md`, `backend.md` y el resultado en este plan.
- Verificación a mano con el teléfono y la tablet en la misma cuenta:
  - una nota creada en uno aparece en el otro;
  - editar en los dos sin conexión: gana la última edición;
  - borrar;
  - apagar el flag para la instalación del teléfono, crear una nota, prenderlo y ver que llega;
  - cerrar sesión con notas pendientes muestra el aviso.

## Riesgos

- **Relojes desfasados:** con más de una hora de adelanto, la nube rechaza la nota y queda pendiente. Es el mismo riesgo aceptado en ADR 0011.
- **App modificada con el flag apagado:** puede escribir notas en su propia cuenta. Se acepta hasta que haya cobro (ADR 0016).
- **Remote Config sin conexión la primera vez:** vale el valor por defecto, prendido. Apagarlo para una cuenta que nunca bajó la configuración no tiene efecto hasta que la baje.
