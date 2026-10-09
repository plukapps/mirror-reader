# Plan — Arranque y bienvenida (Android)

**Objetivo:** pantalla de arranque amarilla con el logo y pantalla de bienvenida "01 — Welcome" de `design/Margin Ebook App.dc.html`.

**Specs que implementa:** WEL-001 a WEL-008 (`specs/product/08-welcome.md`).
**Rama:** `feature/splash-intro` (worktree `.claude/worktrees/splash-intro`, desde `master`). **Tarjetas:** K-099, K-101 a K-104 (K-100 queda en Backlog: "Iniciar sesión"). Pedido del usuario (2026-10-09).

## Diseño (medidas del HTML)

- Fondo `#FBD256` (`MarginColors.Yellow`), texto tinta `#130000`, texto secundario `#3A3115`.
- Logo: cuatro barras (una inclinada) en un `viewBox` 110 × 88, a 30 × 24 dp junto a "margin." (20 sp, peso 500, interletra −0,03 em). Margen 28 dp a los lados, 28 dp arriba.
- Titular: 54 sp, interlineado 1,02, peso 400, interletra −0,045 em, 64 dp debajo del logo.
- Bajada: 15 sp, interlineado 1,45, ancho máximo 250 dp, 24 dp debajo del titular.
- Abajo: botón píldora de 48 dp con borde de 1,5 dp, relleno horizontal 30 dp, 16 sp ("Comenzar"), y al lado un círculo de 48 dp con la flecha. 28 dp de margen inferior.
- Pie: 11 sp, "margin. 2026 ©" a la derecha (a la izquierda iba "Sign in": fuera de alcance, K-100).
- La tipografía del diseño es Schibsted Grotesk; la app usa Host Grotesk (K-042) y se mantiene.

## Decisiones

- **Arranque sin librería nueva (WEL-001, WEL-002).** Android 12 o superior: atributos del tema en `values-v31` (`windowSplashScreenBackground` amarillo y `windowSplashScreenAnimatedIcon` con el logo en un vector de 288 dp con el dibujo dentro del círculo central). Android 8 a 11: `windowBackground` del tema con un `layer-list` (amarillo y logo centrado). No se usa `androidx.core:core-splashscreen` ni `setKeepOnScreenCondition`: nada retiene el arranque. Se verifica contra la documentación oficial de Android antes de escribirlo.
- **Estado de la bienvenida (WEL-003, WEL-005).** Interfaz `WelcomeRepository` en `domain` (`completed: Flow<Boolean>`, `markCompleted()`), implementada con la misma DataStore de preferencias (clave `welcomeCompleted`). Regla pura `startDestination(completed, signedIn)`: bienvenida si no se completó o si no hay sesión; si no, Inicio.
- **Decisión una vez por arranque (WEL-003, WEL-006).** `MainViewModel` expone `start: StateFlow<Start>` (`Loading`, `Welcome`, `Home`) con el primer valor de la marca y de `AccountRepository.user` (Firebase entrega la sesión guardada al registrar el listener, sin red). Mientras es `Loading`, la app dibuja solo el fondo amarillo. Si la sesión cambia con la app abierta, no se reubica al usuario.
- **Navegación.** Ruta nueva `welcome` como destino inicial cuando corresponde. "Comenzar" marca la bienvenida y navega a Inicio sacando `welcome` de la pila (WEL-005). Sin barra inferior en la bienvenida.
- **"Abrir con" (WEL-008).** Sin cambios: el lector se apila sobre la pantalla de inicio que toque. No marca la bienvenida.
- **Barras del sistema.** Íconos oscuros sobre el amarillo (el `enableEdgeToEdge` actual ya lo hace en tema claro). Se verifica a ojo.

## Tareas

### Tarea 1: spec y plan (K-099)
- [x] `specs/product/08-welcome.md`, índice de specs y tarjetas.
- [x] Este plan.

### Tarea 2: arranque amarillo con el logo (K-101) (S)
- Logo como vector (`ic_margin_logo.xml`) y vector del arranque con el área segura de Android 12.
- `themes.xml` (`windowBackground` con `layer-list`) y `values-v31/themes.xml` (atributos del arranque).
- **Aceptación:** al abrir la app en frío se ve amarillo con el logo y pasa a la app sin demora agregada (WEL-001, WEL-002).
- **Verificación:** compila; a ojo en el teléfono (Android 12 o superior). No hay test automático útil para el arranque del sistema.
- **Archivos:** `res/drawable/ic_margin_logo.xml`, `res/drawable/splash_icon.xml`, `res/drawable/splash_background.xml`, `res/values/themes.xml`, `res/values-v31/themes.xml`.

### Tarea 3: regla y estado de la bienvenida (K-102) (S)
- `WelcomeRepository` + `WelcomeRepositoryImpl` (DataStore) + binding de Hilt.
- Regla pura `startDestination` y `MainViewModel.start`.
- **Aceptación:** sin marcar → bienvenida; marcada sin sesión → bienvenida; marcada con sesión → Inicio; antes de leer los datos → `Loading` (WEL-003, WEL-006).
- **Verificación:** tests JVM (`./gradlew :app:testDebugUnitTest`) de la regla y de `MainViewModel` con repositorios falsos, que citan WEL-003 y WEL-006. Test de emulador del repositorio (compilado; correrlo es aparte).
- **Archivos:** `domain/repository/WelcomeRepository.kt`, `domain/StartDestination.kt`, `data/repository/WelcomeRepositoryImpl.kt`, `di/RepositoryModule.kt`, `ui/MainViewModel.kt`, tests.

### Tarea 4: pantalla de bienvenida y navegación (K-103) (M)
- `WelcomeScreen` según el diseño, con desplazamiento si no entra (apaisado, tablet) y descripciones para TalkBack.
- Textos en `strings.xml`.
- `AppNavHost` con destino inicial variable, fondo amarillo mientras es `Loading`, ruta `welcome` y "Comenzar" → Inicio sin volver atrás.
- **Aceptación:** WEL-004, WEL-005, WEL-006, WEL-007.
- **Verificación:** tests JVM pasan; `WelcomeScreenTest` de emulador (texto visible, "Comenzar" y la flecha llaman a la acción, nombres accesibles) compilado; a ojo en el teléfono.
- **Archivos:** `ui/welcome/WelcomeScreen.kt`, `ui/navigation/AppNavHost.kt`, `MainActivity.kt`, `res/values/strings.xml`, `androidTest/.../welcome/WelcomeScreenTest.kt`.

### Checkpoint: después de la Tarea 4
- Tests JVM pasan y compilan los de emulador.
- Flujo completo en el teléfono: instalación limpia → arranque → bienvenida → Comenzar → Inicio → atrás sale; reabrir → Inicio (con sesión) o bienvenida (sin sesión).

### Tarea 5: cierre (K-104) (XS)
- `specs/platforms/android.md` si corresponde, resultado en este plan, Kanban.
- Verificación a mano en teléfono (y en la tablet si está a mano), incluido "Abrir con" un EPUB con la bienvenida pendiente (WEL-008).

## Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| En Android 12+ el ícono del arranque se recorta o queda chico si no respeta el área segura | Medio | Vector de 288 dp con el logo dentro del círculo de 192 dp; se revisa a ojo |
| El arranque de Android 8–11 deja el `windowBackground` amarillo detrás de pantallas sin fondo propio | Bajo | Todas las pantallas dibujan su fondo (`Surface` en `MainActivity`); se revisa el lector |
| En una instalación limpia, la sesión de desarrollo se abre después de decidir | Bajo | Esperado: la primera vez se muestra la bienvenida de todos modos |
| Sin credenciales de desarrollo, la bienvenida aparece en cada arranque | Bajo | Es lo que pide WEL-003; se avisa al probar |

## Preguntas abiertas

Ninguna.
