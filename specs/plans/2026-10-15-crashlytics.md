# Plan — Registro de cierres con Crashlytics (Android e iOS)

**Objetivo:** que los cierres inesperados de las dos apps lleguen a Firebase Crashlytics, asociados al `uid` de la cuenta y sin datos personales ni contenido.

**Specs:** CMP-010 (nuevo). **Decisión técnica:** ADR 0018.
**Rama:** `claude/crashlytics-android-ios-b61f44`, desde `develop`. PR contra `develop`. **Tarjetas:** K-333 a K-336.
**Pedido del usuario (2026-10-15):** integrar Crashlytics en Android e iOS y armar el PR.

## Tareas

### K-333 — Spec, ADR y plan
- CMP-010 en `06-store-compliance.md`, ADR 0018, este plan y las tarjetas.

### K-334 — Android
- `firebase-crashlytics` por el BoM y el plugin `com.google.firebase.crashlytics` en `libs.versions.toml` y los dos `build.gradle.kts`.
- Dominio: `CrashReporter` y `CrashReportIdentity` (sigue `AccountRepository.user` y manda el `uid`, o vacío sin sesión). Arranca en `ReaderApp.onCreate`.
- Datos: `FirebaseCrashReporter`. DI en `FirebaseModule`/`RemoteModule`.
- Test JVM `CrashReportIdentityTest` (CMP-010): con sesión manda el `uid` y no el email; al cerrar sesión, vacío; no repite si el `uid` no cambia.

### K-335 — iOS y Mac
- `FirebaseCrashlytics` en `project.yml`, `dwarf-with-dsym` y la fase `Crashlytics/run` (solo con `GoogleService-Info.plist`).
- Dominio (`ReaderDomain`): `CrashReporter` y `CrashReportIdentity.update(_:)`, que no repite el mismo `uid`.
- Datos: `FirebaseCrashReporter` y un oyente del estado de Auth que llama a `update` (arranca en `AppGraph` con Firebase).
- Test de dominio `CrashReportIdentityTests` (CMP-010), los mismos casos que Android.

### K-336 — Verificación a mano (después del merge, con el usuario)
- Habilitar Crashlytics en la consola de `mirror-reading-staging` (pedirlo al usuario).
- Forzar un cierre de prueba en el teléfono y en el iPhone, y ver el reporte con el `uid` en la consola. El botón de prueba no se deja en la app: se fuerza con un build local.

## Criterios de terminado
- Tests JVM de Android y de dominio de Apple en verde; la app de Apple compila para iPhone y Mac.
- Un clon sin `google-services.json`/`GoogleService-Info.plist` sigue comportándose como antes (Android exige el archivo desde ADR 0005; Apple compila sin él).
