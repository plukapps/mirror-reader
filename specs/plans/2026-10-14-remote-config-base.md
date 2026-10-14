# Plan — Base compartida de Remote Config (backend y Android)

**Objetivo:** que la sincronización de notas (plukapps/mirror-reader#38) y el catálogo de planes (plukapps/mirror-reader#39, #40) usen la misma base de Remote Config: un solo template versionado y la misma infraestructura de interruptores en Android.

**Specs:** ACC-011 (nuevo). **Decisión técnica:** ADR 0016.
**Rama:** `feature/remote-config`, desde `develop`. PR contra `develop`. Las ramas de #38 y #39 la integran. **Tarjetas:** K-310 a K-312.
**Pedido del usuario (2026-10-14):** separar en un PR aparte lo de Remote Config que salió de #38, porque otro PR necesita lo mismo.

## Contenido

- **Backend:**
  - `remoteconfig.template.json` con `plans` (igual al publicado y al de #39) y `notes_sync_enabled`;
  - `firebase.json` → `remoteconfig`;
  - la sección del README;
  - `tests/remoteconfig.test.mjs`.
- **Android:**
  - `firebase-config` por el BoM;
  - `domain/flags` (`Feature`, `FeatureFlags`, `RemoteFlagSource`, `RemoteFeatureFlags`);
  - `data/flags/FirebaseRemoteFlagSource`;
  - DI y arranque dentro de `CloudListeners`;
  - `RemoteFeatureFlagsTest` y `FeatureTemplateTest`.
- **Specs:** ACC-011, ADR 0016 e índice.
- **Afuera:** lo que usa el interruptor (la sincronización de notas, #38) y la lectura de `plans` (#39). iOS cuando tenga notas.

## Tareas

### K-310 — Plan y rama
- Este plan y las tarjetas.

### K-311 — Template compartido y tests
- Template, `firebase.json`, README y test de backend.
- Publicar desde este archivo, a pedido del usuario: vuelve `notes_sync_enabled`, que el deploy de #39 borró.

### K-312 — Infraestructura de interruptores en Android
- Lo listado arriba. Los tests JVM pasan.

### Después del merge
- `feature/notes-sync` (#38) integra esta rama.
- La rama de #39 la integra también, o rebasa sobre ella. Su template queda con los dos parámetros.
