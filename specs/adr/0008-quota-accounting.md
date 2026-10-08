# ADR 0008 — Cuota de espacio contada por una Cloud Function

**Estado:** provisional. Depende del ADR 0007.

## Contexto

ACC-002, ACC-003 y LIB-009 piden una cuota de espacio por usuario: se muestra el uso, se bloquean nuevas subidas al llegar al límite y nunca se bloquea la lectura ni se borra nada.

Lo que verificamos (plan `2026-10-07-backend-firebase.md`, Tarea 4):

- Las reglas de Storage pueden leer Firestore con `firestore.get()` (máximo 2 documentos por evaluación, cuenta como lectura de Firestore). Pueden rechazar una subida si `usedBytes + tamaño > quotaBytes`.
- Las reglas de Firestore no pueden leer el tamaño real de un archivo de Storage.
- Por lo tanto, si el cliente escribe `usedBytes` o `sizeBytes`, puede mentir y subir más de su cuota. Cada archivo está acotado a 100 MB, pero la cantidad no.

## Opciones

1. **El cliente actualiza `usedBytes`** en una transacción, validada por reglas. Sin servidor, pero el valor no es confiable.
2. **Una Cloud Function mantiene `usedBytes`** a partir de los eventos de Storage, con el tamaño real. Las reglas de Storage usan ese valor para rechazar subidas.
3. **Función como único camino de subida** (la app sube a la función). Descartada: pierde las subidas reanudables del SDK y aumenta costo y complejidad.

## Decisión

Opción 2.

- `onObjectFinalized` y `onObjectDeleted` sobre `users/{uid}/books/{sha256}.epub` actualizan `users/{uid}.usedBytes` con el tamaño real del objeto.
- Idempotencia: los eventos llegan al menos una vez. Cada archivo contabilizado se registra en `users/{uid}/storedFiles/{sha256}` (solo escribe el servidor). Sumar o restar ocurre una sola vez, en una transacción.
- Las reglas de Storage rechazan crear un archivo si `usedBytes + tamaño > quotaBytes`. Leer y borrar no dependen de la cuota.
- El cliente nunca escribe `plan`, `quotaBytes`, `usedBytes` ni `storedFiles` (ya garantizado por las reglas de Firestore).

## Consecuencias

- Se agrega `code/backend/v1/functions` (TypeScript, Node 22) y el emulador de Functions. Desplegar funciones requiere el plan Blaze (ya requerido por Storage).
- **Condición de carrera conocida:** varias subidas simultáneas pueden pasar la regla con el mismo `usedBytes` antes de que la función lo actualice. El exceso queda acotado por la cantidad de subidas en paralelo, cada una de hasta 100 MB. Se acepta en v1. Si hace falta, se corrige después (por ejemplo, reservando espacio antes de subir).
- La cuenta es eventual: `usedBytes` se actualiza segundos después de la subida.
- Cada subida cuesta una lectura de Firestore por la regla, más las de la función.
- `storedFiles` permite reconciliar `usedBytes` si alguna vez se desvía.
- El tamaño del documento del libro (`sizeBytes`) es informativo para la interfaz. La cuota usa solo el valor del servidor.

## Nota (2026-10-08): las portadas no cuentan

Las portadas (`users/{uid}/covers/{hash}.jpg`, LIB-012) quedan fuera de `usedBytes`. Pesan unas decenas de KB, tope de 1 MiB por regla, y solo se pueden subir si existe el documento del libro, así que su cantidad queda acotada por la de libros. Contarlas obligaría a cambiar las funciones y sus tests sin un beneficio real. Si el costo de almacenamiento lo pidiera, se reevalúa junto con la deuda K-050.
