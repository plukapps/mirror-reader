# Specs — Lector de EPUB multiplataforma

Fuente de verdad del producto (SDD: primero spec, después código). Si el código y el spec difieren, se corrige uno de los dos en el mismo cambio.

## Índice

- [00-vision.md](00-vision.md) — problema, principios, éxito
- [roadmap.md](roadmap.md) — fases
- [open-questions.md](open-questions.md) — decisiones pendientes
- product/
  - [01-library.md](product/01-library.md)
  - [02-reader.md](product/02-reader.md)
  - [03-annotations.md](product/03-annotations.md)
  - [04-sync.md](product/04-sync.md)
  - [05-account-and-plans.md](product/05-account-and-plans.md)
  - [06-store-compliance.md](product/06-store-compliance.md)
  - [07-home.md](product/07-home.md)
- [data-model.md](data-model.md)
- platforms/[android.md](platforms/android.md)
- adr/ — decisiones técnicas ([0001](adr/0001-native-clients.md), [0002](adr/0002-offline-first.md), [0003](adr/0003-backend-supabase.md), [0004](adr/0004-epub-engine-readium.md), [0005](adr/0005-android-stack-and-architecture.md), [0006](adr/0006-library-local-storage.md))

## Convenciones

- Idioma: español. IDs y términos técnicos en inglés.
- Requisitos con ID estable: `LIB-001`, `RDR-001`, `ANN-001`, `SYN-001`, `ACC-001`, `CMP-001`. No se reutilizan IDs.
- Verbos: **debe** (obligatorio v1), **puede** (opcional).
- Escenarios en formato Dado / Cuando / Entonces.
- Código, tests y commits referencian el ID (ej. `LIB-003`).
- Los specs de `product/` no nombran tecnología. Eso vive en `adr/`.
- Estado de ADR: provisional, aceptado, reemplazado.
