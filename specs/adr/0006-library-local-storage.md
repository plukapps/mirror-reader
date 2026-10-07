# ADR 0006 — Almacenamiento local de la biblioteca

**Estado:** provisional.

## Contexto

La biblioteca guarda los EPUB del usuario (LIB-001, LIB-003). Hoy el lector abre un libro por la URI que devuelve el selector de archivos. Esa URI depende de un permiso que el proveedor puede revocar, y el archivo original puede moverse o borrarse.

## Decisión

Al importar, el EPUB se copia al almacenamiento privado de la app (`files/books/<hash>.epub`). El nombre es el hash SHA-256 del contenido, que es también el identificador del libro (LIB-003). La portada se guarda como archivo aparte (`files/covers/<hash>`). Los metadatos y el estado viven en Room.

## Consecuencias

- La biblioteca funciona sin conexión y sin permisos de URI (ADR 0002).
- Importar dos veces el mismo archivo es detectable por el hash y no duplica.
- El libro ocupa el doble hasta que el usuario borre el original.
- El lector se abre por `bookId`, no por URI.
- Alternativa descartada: guardar solo la URI persistente. Falla si el archivo se mueve o se borra.
