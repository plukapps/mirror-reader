# Plan — Onboarding en iOS (O1 a O9)

**Objetivo:** el mismo onboarding que Android (plan `2026-10-10-android-onboarding.md`): alta con email o Google, verificación del email, intereses, meta diaria con recordatorio, ingreso y recuperación de contraseña, según el diseño O1 a O9 de `design/Margin Ebook App.dc.html`.

**Specs que implementa:** ONB-001 a ONB-022 (`specs/product/09-onboarding.md`). **Decisión técnica:** ADR 0015.
**Rama:** `feature/ios-onboarding` (desde `develop`), worktree `.claude/worktrees/ios-onboarding`. **Tarjetas:** K-160 a K-166. Pedido del usuario (2026-10-12): implementarlo tal cual Android, sin pedir confirmación.

## Diseño

Las mismas medidas que Android (sección "Diseño" de su plan), en puntos: fondos amarillo `#FBD256` (O1, O6) y papel `#F1F1F1`; botón principal de 56 pt en tinta con texto amarillo; campos de 50 pt con radio 16; chips de 40 pt; opciones de meta de 60 pt; tarjeta del recordatorio en tinta. Host Grotesk. Columna de ancho máximo 560 pt (ONB-022).

## Decisiones

- **Dominio (`ReaderDomain`):** `AccountUser` suma `displayName` y `needsEmailVerification` con valores por defecto. `AuthRepository` (async throws, falla con `AuthFailure`), `AuthError`, `checkPassword`, `isValidEmail`, `firstName`, `startDestination`, `Genre`, `ReadingGoal`, `DailyReminder`, `OnboardingPreferences`, `ReminderScheduler`, `parseAuthLink`. Sale `DevCredentials` y `LibrarySync` sincroniza solo con sesión.
- **Datos:** `FirebaseAccountRepository` implementa también `AuthRepository`, con `AuthErrorCode` → `AuthError`. `GoogleSignInClient` (GoogleSignIn 9.1.0) entrega los tokens. `UserDefaultsOnboardingPreferences` (claves de Android). `NotificationReminderScheduler` con `UNUserNotificationCenter`.
- **ViewModels:** `@MainActor @Observable`, uno por pantalla, con el destino siguiente como un campo (`next`) que la vista consume y limpia, como Android. Las esperas (60 s, 30 s, consulta cada 3 s) reciben la duración por inicializador para los tests.
- **Arranque (`AppStartViewModel`):** decide una vez por arranque con la sesión que el dispositivo ya conoce (ONB-019). Sin Firebase, Inicio (ADR 0015, punto 9).
- **Navegación:** `OnboardingFlow` con `NavigationStack` y una ruta por pantalla (`signUp(name:email:)`, `verify(justSent:)`, `interests`, `goal`, `allSet`, `signIn`, `forgot(email:)`, `reset(code:)`). Empezar de nuevo reemplaza la raíz y vacía la pila; ir a Inicio cambia la raíz de la app, así atrás nunca vuelve al onboarding. "Importar mis libros" abre el selector de Archivos al aparecer Inicio (ONB-013).
- **Enlaces:** `onOpenURL` reparte: primero Google, después la URL de acción de Firebase (`parseAuthLink`: O9 o aplicar la verificación), y lo demás es un EPUB (LIB-001).
- **"Abrir email":** abre Mail con `message://`.

## Tareas

### Tarea 1: ADR, plan y Kanban (K-160) (S)
- ADR 0015, este plan, índice de specs, ONB-022 menciona VoiceOver, tarjetas.

### Tarea 2: dominio y reglas (K-161) (M)
- Tipos y reglas de la lista de arriba; fuera `DevCredentials` de `LibrarySync`.
- **Verificación:** `swift test`, con los casos de `OnboardingRulesTest` de Android (ONB-004, ONB-005, ONB-009, ONB-010, ONB-013, ONB-019, enlaces).

### Tarea 3: datos (K-162) (M)
- `FirebaseAccountRepository`, mapeo de errores, Google, preferencias en `UserDefaults`, GoogleSignIn en `project.yml`, esquema de URL, `tools/copy-google-client.sh`; fuera la cuenta de desarrollo.
- **Verificación:** compila en iPhone y Mac; test de las preferencias en el simulador. Firebase no se toca en tests (se verifica a mano).

### Tarea 4: ViewModels (K-163) (M)
- `SignUpViewModel`, `VerifyEmailViewModel`, `InterestsViewModel`, `GoalViewModel`, `AllSetViewModel`, `SignInViewModel`, `ForgotPasswordViewModel`, `ResetPasswordViewModel`, `AppStartViewModel`.
- **Verificación:** los tests de `OnboardingViewModelsTest` y `StartTest` de Android traducidos, en el simulador.

### Tarea 5: pantallas y navegación (K-164) (L)
- Componentes comunes, O1 a O9, `OnboardingFlow`, raíz de la app, enlaces, VoiceOver.
- **Verificación:** compila en iPhone y Mac; capturas en el simulador de O1, O2, O4, O5 y O7.

### Tarea 6: recordatorio diario (K-165) (S)
- `NotificationReminderScheduler` (permiso, notificación diaria, cancelar).
- **Verificación:** test del contenido de la notificación (título, texto, hora); a mano en el simulador.

### Tarea 7: cierre (K-166) (XS)
- `apple.md`, `code/apple/README.md`, `AGENTS.md`, resultado en este plan, Kanban.

## Resultado (2026-10-12)

- Dominio: `swift test` 155 pasan (9 nuevos en `OnboardingRulesTests`; salen 3 de la cuenta de desarrollo en `LibrarySyncTests`). Mutación de ONB-005 detectada.
- App en el simulador de iPhone: 132 pasan (38 nuevos: `OnboardingViewModelsTests` 31, `OnboardingDataTests` 7). Mutaciones de ONB-008 (borrar una cuenta ya verificada) y ONB-011 (recordatorio sin permiso) detectadas.
- Compila en iPhone y Mac. Los tests de la Mac no arrancan desde antes (K-123).
- Capturas de O1 a O8 en un simulador limpio (creado para esto, "Onboarding iPhone 16"), con un gancho temporal que abría cada ruta y ya no está en el código.
- Cambios respecto de Android: íconos SF Symbols; la hora del recordatorio se elige con la rueda de iOS; el aviso de permiso negado es una alerta antes de seguir (en Android, un toast); el recordatorio no necesita receptor de reinicio. Sin "Iniciar sesión con Apple" (K-168) ni O9 desde el enlace (K-167).
- Sin commits: quedan para cuando el usuario los apruebe.

### Verificación a mano pendiente (K-166)

1. Simulador limpio: arranque → O1. Crear cuenta con email → O3 → abrir el enlace en Safari → volver → O4 → O5 (permiso) → O6 → Inicio; no se vuelve al onboarding.
2. Cerrar la app en O3 y reabrir: vuelve a O3. "Cambiar" vuelve a O2 con nombre y email.
3. Contraseña equivocada (error en rojo bajo la contraseña), luego bien → Inicio y la biblioteca se sincroniza. Google (en iPhone y en Mac).
4. "¿Olvidaste tu contraseña?" → enlace → cambio en la página de Firebase → ingresar con la nueva.
5. Recordatorio a 2 minutos: llega la notificación y abre la app.
6. Apaisado, teclado abierto en O2 y O7, VoiceOver en O2, O4 y O5.

## Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| GoogleSignIn 9.1.0 no resuelve junto a Firebase 12.14.0 o no compila con Xcode 16.2 | Medio | Rangos de dependencias verificados en los `Package.swift`; si falla, 9.0.0 (tools 5.5) |
| Sin `GOOGLE_REVERSED_CLIENT_ID`, Google no vuelve a la app | Bajo | Script que lo copia; el botón avisa el error |
| O9 no abre en la app sin Associated Domains | Bajo | La página de Firebase cambia la contraseña igual; tarjeta propia |
| La App Store exige "Iniciar sesión con Apple" | Alto para publicar | Tarjeta propia antes de publicar |
| Borrar en "Cambiar" una cuenta ya verificada | Medio | Se recarga el usuario antes, como Android |

## Preguntas abiertas

Ninguna (decisiones tomadas sin consultar por pedido del usuario; quedan en ADR 0015).
