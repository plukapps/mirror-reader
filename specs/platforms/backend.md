# Backend (Firebase)

Alcance del backend v1 y modelo de datos. Decisión técnica: `specs/adr/0007-backend-firebase.md`. Código: `code/backend/v1`. Plan: `specs/plans/2026-10-07-backend-firebase.md`.

## Principios

- Cada usuario solo accede a lo que está bajo `users/{uid}` con su propio `uid` (ACC-004). Sin acceso anónimo.
- Todo lo que no está descrito aquí está denegado por defecto. Una colección nueva necesita su regla y sus tests.
- Las marcas de tiempo las pone el servidor (`createdAt`, `updatedAt` iguales a la hora de la solicitud), no el reloj del dispositivo. Así "gana el último cambio" (SYN-006) no depende del teléfono.
- Los documentos no se borran desde el cliente: se marcan con `deletedAt` (SYN-007). La limpieza definitiva es trabajo del servidor, fuera de este plan.
- El cliente no puede cambiar su plan ni su cuota (ACC-002, ACC-005).

## Colecciones

### `users/{uid}`

| Campo | Tipo | Notas |
|---|---|---|
| `plan` | string | `"free"` en v1. Nuevos planes sin cambiar el modelo (ACC-005). |
| `quotaBytes` | int | Cuota del plan, en bytes. Plan gratuito: 15 MiB (15 728 640). |
| `usedBytes` | int | Bytes usados por los archivos del usuario (ACC-003). Lo mantiene una Cloud Function con el tamaño real (ADR 0008). |
| `createdAt` | timestamp | Alta de la cuenta. |

Reglas: el dueño puede leer (así la app muestra el espacio usado y libre, ACC-003). **Nadie puede escribir desde el cliente.** `usedBytes` lo escribe el servidor (ADR 0008). El documento lo crea la función `onUserCreated` al registrarse (email o Google, ACC-001) con `plan: "free"`, `quotaBytes` de 15 MiB (provisional, open-questions #2) y `usedBytes: 0`. Es idempotente: un evento repetido no pisa un documento existente. Subir un libro antes de que exista el documento se rechaza (la regla de Storage lo exige), así que la app debe esperar a que aparezca tras el registro.

### `users/{uid}/storedFiles/{sha256}`

Registro interno de archivos ya contabilizados en `usedBytes`. Existe para que sumar o restar ocurra una sola vez aunque el evento de Storage llegue repetido.

| Campo | Tipo | Notas |
|---|---|---|
| `sizeBytes` | int | Tamaño real del archivo, tomado de Storage. |
| `createdAt` | timestamp | Del servidor. |

Reglas: **ni el dueño puede leer ni escribir.** Solo el servidor.

### `users/{uid}/books/{bookId}`

`bookId` es el SHA-256 del archivo en hexadecimal minúscula (64 caracteres), igual que el identificador local en Android.

| Campo | Tipo | Notas |
|---|---|---|
| `title` | string | 1 a 500 caracteres. |
| `authors` | list de string | Hasta 50 autores. Puede estar vacía. |
| `filePath` | string | Debe ser `users/{uid}/books/{bookId}.epub`. No cambia. |
| `sizeBytes` | int | Tamaño del archivo, mayor o igual a 0. No cambia. |
| `createdAt` | timestamp | Del servidor. No cambia. |
| `updatedAt` | timestamp | Del servidor, en cada escritura. |
| `deletedAt` | timestamp o null | Marca de borrado (SYN-007, LIB-008). `null` si el libro está vivo. |

Reglas: el dueño lee, crea y actualiza. No hay borrado físico desde el cliente.

### `users/{uid}/collections/{collectionId}`

| Campo | Tipo | Notas |
|---|---|---|
| `name` | string | 1 a 100 caracteres. |
| `bookIds` | list de string | Hasta 5000 ids. Un libro puede estar en varias colecciones (LIB-005). |
| `createdAt` | timestamp | Del servidor. No cambia. |
| `updatedAt` | timestamp | Del servidor, en cada escritura. |
| `deletedAt` | timestamp o null | Marca de borrado. |

Reglas: como los libros.

## Archivos (Storage)

Ruta `users/{uid}/books/{sha256}.epub`. Solo el dueño lee, sube y borra (ACC-004). Solo `application/epub+zip`, de 1 byte a 100 MB, y un archivo no se sobrescribe: el nombre es su hash (LIB-002).

### Cuota

- Se rechaza una subida si `usedBytes` + tamaño del archivo supera `quotaBytes` (ACC-002, LIB-009). Leer y borrar nunca dependen de la cuota.
- `usedBytes` lo actualizan las funciones `onBookFileFinalized` y `onBookFileDeleted` (código en `code/backend/v1/functions`), con el tamaño real del objeto.
- Límite conocido: subidas simultáneas pueden pasar la regla con el mismo `usedBytes` antes de que la función lo actualice. El exceso queda acotado (ADR 0008).
- La cuenta es eventual: `usedBytes` se actualiza segundos después de la subida o el borrado.

## Riesgos conocidos

Revisión de seguridad del checkpoint de reglas (2026-10-07). Lo que no está resuelto y se decide aparte (tarjeta K-050):

- **Cuentas descartables y costo.** Cualquiera puede registrarse y usar su cuota completa. Las reglas no exigen correo verificado ni App Check, así que muchas cuentas falsas multiplican el uso de almacenamiento y las lecturas de Firestore que cada subida provoca. Mitigaciones posibles: App Check, exigir `email_verified` para subir, alerta de presupuesto.
- **Documentos sin tope de cantidad.** La cuota cuenta solo bytes de Storage. Un usuario puede crear muchos documentos de libro o colección sin archivo, y eso no consume cuota. Cada documento está acotado (1 MiB, campos validados), pero la cantidad no. Mitigación posible: contador por usuario o tope en las funciones.
- **Elementos de listas sin validar.** Las reglas no pueden recorrer listas, así que `authors` y `bookIds` solo limitan el tamaño de la lista, no el tipo ni el largo de cada elemento. Queda acotado por el tamaño máximo del documento.
- **Cuota inflada por eventos fuera de orden.** Si el evento de borrado llega antes que el de creación, `usedBytes` queda sumando un archivo que ya no existe. Se puede reconciliar con `storedFiles` (ADR 0008). No es un problema de seguridad, pero perjudica al usuario.
- **Subidas simultáneas** pueden exceder la cuota (ver ADR 0008).

Revisado y sin hallazgos: acceso por usuario en Firestore y Storage, denegado por defecto, el cliente no escribe plan, cuota ni `usedBytes`, no hay borrado físico, un archivo no se sobrescribe, sin secretos versionados, `npm audit` de producción y de `functions` sin vulnerabilidades.

## Fuera de este modelo por ahora

Posición de lectura, preferencias y anotaciones (SYN-002, ANN): su modelo y sus reglas llegan con esas rebanadas. Mientras tanto están denegadas.
