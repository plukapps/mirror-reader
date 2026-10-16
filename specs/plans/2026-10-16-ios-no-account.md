# Plan — Uso sin cuenta, iPhone

**Objetivo:** lo mismo que en Android (`2026-10-15-android-no-account.md`): la app de iPhone se usa sin registrarse, solo en el dispositivo, con la cuenta como opción para sincronizar.

**Specs:** `13-no-account.md` (GST-001 a GST-026), HOM-012, ONB-003 y ONB-009 (retirado). **Decisiones técnicas:** ADR 0019 (datos locales sin dueño) y ADR 0016 (flags y números de Remote Config).
**Rama:** `feature/no-account-ios`, desde `feature/no-account` (que tiene el spec y Android). PR contra `feature/no-account`. **Tarjetas:** K-355 a K-362. La Mac queda afuera, como Perfil (K-078).
**Pedido del usuario (2026-10-16):** armar el plan de iOS y ejecutarlo.

## Punto de partida (revisado en el código)

- `AppStartViewModel` decide el arranque una vez con `startDestination(user)`. Sin Firebase (sin `GoogleService-Info.plist`) ya abre en Inicio.
- La sincronización no hace nada sin sesión (`LibrarySync` devuelve `.noSession`) y los oyentes se cuidan del usuario nulo. La línea de estado exige `hasSession`, pero `SyncState` arranca con `hasSession: true`: puede verse "Sincronizando…" un instante sin cuenta.
- SwiftData no guarda el uid. Lo pendiente se marca por fila (`uploadedAt == nil`, etc.). Quitar un libro que nunca se subió no deja borrado para la nube. Igual que Android (ADR 0019).
- **No hay un flujo de la sesión:** la primera pasada después de entrar sale de `RootView.task`. Por eso el alta y el ingreso desde adentro salen de Inicio y vuelven a entrar, como hoy cerrar sesión (`backToOnboarding`): `RootView` se recrea y sincroniza.
- `LocalAccountWiper` borra una lista de claves (`accountKeys`). Las claves nuevas de esta rebanada van a esa lista.
- `ImportQuotaCheck` muestra la hoja de archivo muy grande aunque no haya cuota. Sin sesión no debe aparecer (GST-006).
- Remote Config solo lee booleanos (y el JSON de planes). Hace falta leer números (GST-026).

## Tareas

Cada tarea: tarjeta a "En curso", test primero (Swift Testing; dominio con `swift test`, app con `xcodebuild … test` en el simulador), código, tests en verde, tarjeta a "Revisión" y un commit que nombra la tarjeta.

### K-355 — Plan y tarjetas

### K-356 — Dominio
- `startDestination(user, welcomeSeen)` (GST-003, GST-004).
- `shouldShowGuestNotice(signedIn, hasBooks, dismissedAt, now, snoozeDays)` y `GuestBannerVariant` (GST-010, GST-023).
- `ImportQuotaCheck` sin sesión no muestra hojas (GST-006).
- `Feature.guestBannerNoBackup` y los números de Remote Config (`Setting.guestNoticeSnoozeDays`, 7 por defecto) en `FeatureFlags` y `RemoteFlagSource` (GST-025, GST-026). Test que compara con `remoteconfig.template.json`.
- `AccountUser.initial` compartido (PRF-005, HOM-012).

### K-357 — Arranque, bienvenida O1 v2, L0 y alta sin intereses
- `welcome_seen` en UserDefaults (y en `accountKeys`). `AppStartViewModel` lo lee.
- `WelcomeView` según O1 v2; `GuestReadyView` (L0) con las portadas de ilustración.
- Se quitan los intereses (O4): verificar y Google van a la meta, pasos "1 de 3" a "3 de 3".
- Tests: `AppStartTests`, `OnboardingViewModelsTests`, `LocalAccountWiperTests`.

### K-358 — Sin nube a la vista
- Sin sesión: sin hojas de cuota, sin aviso del 80 %, sin línea de estado (tampoco el "Sincronizando…" del arranque).
- Confirmación de quitar y FAQ con el caso sin cuenta (GST-008, GST-017).

### K-359 — Inicio: cabecera y banner; aviso en la Biblioteca
- Cabecera con marca y avatar que abre Perfil (HOM-012).
- Banner A/B (GST-023, GST-025) y aviso de L1 en la Biblioteca (GST-010), con `guest_notice_dismissed_at` (en `accountKeys`) y los días de GST-026.

### K-360 — Perfil sin cuenta (09g)
- Tarjeta "Sin cuenta", "Ya tengo una cuenta", sin espacio, contraseña, cerrar sesión, eliminar cuenta ni Wi-Fi; sin leer la cuota (GST-009).

### K-361 — Alta corta e ingreso desde adentro
- `OnboardingRouter` en modo "desde adentro": B1 con cruz y "Paso 1 de 2", O3 con "Paso 2 de 2", B3 (`ShortAllSetView`) y "Volver a la biblioteca". La cruz vuelve a Inicio. El ingreso termina en la Biblioteca (GST-011, GST-021, GST-022).

### K-362 — Cierre de docs y verificación en el simulador
- `specs/platforms/apple.md`, `code/apple/README.md` y AGENTS.md.
- Verificación en un simulador propio ("Sin cuenta iPhone 16"), sin tocar los otros: instalación nueva → L0 → importar → banner → Perfil → crear cuenta → B3.

## Criterios de terminado
- `swift test` y `xcodebuild … test` (iPhone) en verde; la app compila para Mac.
- Todos los GST con un test que los nombra.
- Quien tenía sesión no ve cambios (GST-004).
