# Modelo de datos

Independiente del backend. Define qué existe y cómo se relaciona.

## Regla común

Toda entidad sincronizada tiene: `id` (UUID), `updated_at`, `deleted_at` (marca de borrado, nulo si activa) y `device_id` de origen. Todo pertenece a un usuario.

## Entidades

| Entidad | Campos principales |
|---------|--------------------|
| User | id, email |
| Plan | id, nombre, cuota en bytes |
| Device | id, user, nombre, último contacto |
| Book | hash de contenido, tamaño, referencia al archivo |
| LibraryEntry | user, book, título, autor, portada, fecha de alta |
| Collection | id, user, nombre |
| CollectionBook | collection, library entry |
| ReadingPosition | library entry, ubicación en el EPUB, porcentaje, device |
| Bookmark | library entry, ubicación, fecha |
| Highlight | library entry, ubicación inicio y fin, texto citado, color |
| Note | library entry, texto, highlight opcional, ubicación opcional |
| ReadingPreferences | user, letra, tamaño, interlineado, márgenes, tema |

## Relaciones

- Un Book (archivo) puede estar en la biblioteca de muchos usuarios. Un usuario tiene como máximo una LibraryEntry por Book.
- Collection y LibraryEntry: muchos a muchos.
- Bookmark, Highlight, Note y ReadingPosition cuelgan de LibraryEntry.
- Una Note de conflicto se guarda como Note adicional marcada como conflicto hasta que el usuario resuelva.
