# Plan — Onboarding en Android (O1 a O9)

**Objetivo:** alta, verificación, intereses, meta diaria con recordatorio, ingreso y recuperación de contraseña con Firebase Auth, según el diseño O1 a O9 de `design/Margin Ebook App.dc.html`.

**Specs que implementa:** ONB-001 a ONB-022 (`specs/product/09-onboarding.md`); cambia WEL-003, WEL-004 y WEL-005 (`08-welcome.md`). **Decisión técnica:** ADR 0014.
**Rama:** `feature/onboarding` (desde `master`). **Tarjetas:** K-138 a K-144. Pedido del usuario (2026-10-10): implementar el onboarding completo sin consultas y abrir un PR; autenticación con Firebase.

## Diseño (medidas del HTML)

- Fondos: amarillo `#FBD256` en O1 y O6; papel `#F1F1F1` en el resto. Tinta `#130000`, secundario `#3A3830`/`#3A3115`, apagado `#6B6650`, líneas `#DAD6CA`, campo `#FDFDFD`, error `#8B1E1E`.
- Botón principal: píldora de 56 dp, tinta con texto amarillo, 16 sp peso 700, flecha. Secundario: 56 dp con borde de 1,5 dp. Google: 48 dp con borde y un círculo "G".
- Encabezado de paso: flecha atrás de 44 dp, "Paso N de 4" (12 sp, 600, apagado) u "Omitir" subrayado; barra de 4 tramos de 4 dp con 4 dp de separación.
- Títulos 34 sp (O7: 44 sp), interletra −0,04 em. Campos de 50 dp, radio 16 dp, borde 1,5 dp (2 dp tinta con foco; 2 dp rojo con error), rótulo de 12 sp peso 600 arriba.
- Chips de 40 dp, radio 20 dp: elegido en tinta con ✓ amarillo; libre con borde.
- Opciones de meta de 60 dp, radio 18 dp: elegida en amarillo con borde de 2 dp tinta.
- Tarjeta del recordatorio en tinta, radio 18 dp, campana amarilla, interruptor amarillo.
- Tipografía: Host Grotesk (la del diseño es Schibsted Grotesk; se mantiene la de la app, K-042).

## Decisiones

- **Dominio:** `AccountRepository` queda igual (sesión). `AuthRepository` nuevo con las operaciones del onboarding; ambos los implementa `FirebaseAccountRepository`. `AccountUser` suma `displayName` y `needsEmailVerification` con valores por defecto, para no tocar los fakes existentes.
- **Reglas puras:** `PasswordRules` (ONB-005), `isValidEmail`, `startDestination(user)` (ONB-019), `ReadingGoal` (ONB-010), `canContinueInterests` (ONB-009), `ResendCooldown` (ONB-007, ONB-017).
- **Arranque:** `MainViewModel.start` pasa a `Loading`, `Welcome`, `VerifyEmail`, `Home`. Se quita `WelcomeRepository` (la marca `welcomeCompleted` ya no decide nada).
- **Navegación:** rutas `welcome`, `signup`, `verify`, `interests`, `goal`, `allset`, `signin`, `forgot?email=`, `reset?oobCode=`. Ir a Inicio desde el onboarding limpia toda la pila. Inicio acepta `import=true` para abrir el selector (ONB-013).
- **ViewModels por pantalla**, un `StateFlow<UiState>` cada uno, con eventos de navegación como un campo del estado que la pantalla consume.
- **O9:** `MainActivity` recibe el App Link (`mode=resetPassword`, `oobCode`) y navega a `reset`. `intent-filter` con `autoVerify` sobre `mirror-reading-staging.firebaseapp.com` y `/__/auth/action`.
- **Recordatorio:** `ReminderScheduler` (interfaz en `domain`) implementado con `AlarmManager`; `ReminderReceiver` y `BootReceiver`. Canal de notificaciones "Recordatorio de lectura".

## Tareas

### Tarea 1: spec, ADR y plan (K-138) (S)
- `09-onboarding.md`, `08-welcome.md`, ADR 0014, este plan, índice de specs y tarjetas.

### Tarea 2: dominio y reglas (K-139) (M)
- `AuthRepository`, `AuthError`, `PasswordRules`, `isValidEmail`, `startDestination`, `OnboardingPreferences` (intereses, meta, recordatorio), `ReadingGoal`, `ReminderScheduler`.
- **Verificación:** tests JVM que citan ONB-005, ONB-009, ONB-010 y ONB-019.

### Tarea 3: datos (K-140) (M)
- `FirebaseAccountRepository` implementa `AuthRepository`, mapeo de errores, `OnboardingPreferencesImpl` (DataStore), quitar `DevAccountSignIn` y `WelcomeRepository`, módulos de Hilt, dependencias de Credential Manager.
- **Verificación:** compila; tests JVM del mapeo de errores donde no haga falta el SDK. El repositorio de Firebase no tiene test propio (como antes, ADR 0014): se verifica a mano.

### Tarea 4: ViewModels (K-141) (M)
- `SignUpViewModel`, `VerifyEmailViewModel`, `InterestsViewModel`, `GoalViewModel`, `AllSetViewModel`, `SignInViewModel`, `ForgotPasswordViewModel`, `ResetPasswordViewModel`, y `MainViewModel.start`.
- **Verificación:** tests JVM con repositorios falsos (ONB-004, ONB-006 a ONB-011, ONB-013 a ONB-021).

### Tarea 5: pantallas y navegación (K-142) (L)
- Componentes comunes (`OnboardingScaffold`, botones, campo, barra de pasos), pantallas O1 a O9, botón de Google con Credential Manager, `AppNavHost`, App Link en el manifiesto, textos.
- **Verificación:** compila; test de emulador de la bienvenida actualizado (compila, sin correr: no se corren instrumentados sin preguntar).

### Tarea 6: recordatorio diario (K-143) (S)
- `AlarmReminderScheduler`, receptores, canal, permiso `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- **Verificación:** compila; lógica de la próxima hora en test JVM; a mano en el teléfono.

### Tarea 7: cierre (K-144) (XS)
- `specs/platforms/android.md`, `AGENTS.md` (cuenta de desarrollo), resultado en este plan, Kanban, PR.

## Resultado (2026-10-10)

- Tests JVM: 315 pasan (31 nuevos: `OnboardingRulesTest` 9, `OnboardingViewModelsTest` 22; `StartTest` 5 reemplaza a `WelcomeStartTest`). Mutaciones en `VerifyEmailViewModel` (borrar una cuenta ya verificada) y `GoalViewModel` (recordatorio sin permiso) detectadas.
- Tests de emulador: `WelcomeScreenTest` (3, actualizado a O1) y `AuthErrorsTest` (1) compilan, sin correr (no se corren instrumentados sin consultar; el SDK de Firebase no construye sus excepciones en JVM).
- `assembleDebug` compila. No había dispositivo conectado: **falta la verificación a mano** (K-144).
- Cambios respecto del diseño, decididos sin consulta por pedido del usuario: enlace en lugar de código de 6 dígitos (O3); sin Apple, huella, "Explorar sin cuenta" ni clásicos gratis (O1, O6, O7); el error de O7 no dice intentos restantes; textos en español. Detalle en el spec y en ADR 0014.
- Pendientes en Backlog: URLs de Términos y Privacidad (K-145), `assetlinks.json` para abrir O9 en la app (K-146), cerrar sesión (K-147).

### Verificación a mano pendiente

1. Instalación limpia (o borrar datos): arranque → O1.
2. Crear cuenta con email → O3 → abrir el enlace del email (en el navegador) → volver a la app → O4 → O5 (permiso de notificaciones) → O6 → Inicio; atrás sale de la app.
3. Cerrar la app en O3 sin verificar y reabrir: vuelve a O3. "Cambiar" vuelve a O2 con nombre y email.
4. Ingresar con contraseña equivocada (error en rojo), luego bien → Inicio. Ingresar con Google.
5. "¿Olvidaste tu contraseña?" → enlace → cambio en la página de Firebase → ingresar con la nueva.
6. Recordatorio: elegir una hora a 2 minutos y esperar la notificación; reiniciar el teléfono y ver que sigue.
7. Apaisado, tablet y TalkBack en O2 y O7.

## Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Google falla en otro equipo por SHA-1 no registrado | Medio | Documentado en ADR 0014 y `android.md` |
| O9 no abre en la app sin `assetlinks.json` | Bajo | La página de Firebase cambia la contraseña igual; tarjeta para publicarlo |
| Borrar la cuenta sin verificar en "Cambiar" borra una ya verificada | Medio | Se recarga el usuario antes y, si está verificado, sigue a intereses |
| Recordatorio inexacto (hasta ~1 h de corrimiento en Doze) | Bajo | Aceptable para un recordatorio; el spec dice "cerca de la hora" |

## Preguntas abiertas

Ninguna (decisiones tomadas sin consultar por pedido del usuario; quedan en ADR 0014 y el spec).
