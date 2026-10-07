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
| `quotaBytes` | int | Cuota del plan, en bytes. |
| `usedBytes` | int | Bytes usados por los archivos del usuario (ACC-003). |
| `createdAt` | timestamp | Alta de la cuenta. |

Reglas: el dueño puede leer. **Nadie puede escribir desde el cliente por ahora.** La creación del documento y el manejo de `usedBytes` se definen en K-047 y K-048 (ver plan).

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

## Fuera de este modelo por ahora

Posición de lectura, preferencias y anotaciones (SYN-002, ANN): su modelo y sus reglas llegan con esas rebanadas. Mientras tanto están denegadas.
