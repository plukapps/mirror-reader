# Plan — Perfil: cambiar contraseña, apariencia y solo Wi-Fi (Android e iOS)

**Objetivo:** que tres filas de Perfil que hoy avisan "Llega más adelante." funcionen: "Cambiar contraseña", "Apariencia" y "Sincronizar solo con Wi-Fi".

**Specs que implementa:** PRF-020 a PRF-023 (`specs/product/11-profile.md`). Cambian PRF-008 y PRF-016.
**Base:** rama `claude/tabbar-menu-design-e09011` (PR #25). Cada tarea es una rama y un PR contra esa base. **Tarjetas:** K-203 a K-208.
**Pedido del usuario (2026-10-13):** "cada punto 1 PR para Android, otro para iOS". Decisiones tomadas con el usuario:
- Cambiar contraseña pide la contraseña actual, la nueva y la confirmación.
- Tema oscuro con paleta derivada del diseño, en toda la app salvo el lector y el onboarding.
- Solo con Wi-Fi detiene toda la sincronización, también la posición de lectura.

## Tareas y ramas

| Tarjeta | Rama | Qué |
|---|---|---|
| K-203 | `feature/profile-password-android` | PRF-020 en Android |
| K-204 | `feature/profile-password-ios` | PRF-020 en iOS |
| K-205 | `feature/profile-appearance-android` | PRF-021, PRF-022 en Android (resuelve K-200) |
| K-206 | `feature/profile-appearance-ios` | PRF-021, PRF-022 en iOS |
| K-207 | `feature/profile-wifi-android` | PRF-023 en Android |
| K-208 | `feature/profile-wifi-ios` | PRF-023 en iOS |

Cada PR: tests en verde (Android JVM; Apple dominio e iPhone), notas de la plataforma, tarjeta en Revisión y verificación a mano en el teléfono o el iPhone del usuario (sin tests instrumentados en sus dispositivos sin preguntar). Los PR son independientes: se pueden revisar y fusionar en cualquier orden.

## Cambiar contraseña (PRF-020)

- **Dominio:** `AuthRepository.changePassword(current, new)` (Android) y en `SessionRepository` o un protocolo nuevo (iOS). Las reglas de "Guardar habilitado" son una función pura con test (`canSaveNewPassword(current, new, confirm)`), reutilizando `PasswordRules`/ONB-005 de cada plataforma.
- **Firebase:** reautenticar con `EmailAuthProvider.getCredential(email, current)` (`reauthenticate`) y después `updatePassword(new)`. Los errores se traducen a `AuthError`; credencial inválida o contraseña incorrecta → "La contraseña actual no es correcta." (nuevo caso o mapeo de `invalidCredentials` en esta pantalla).
- **UI:** pantalla con el estilo del onboarding (campos `OnbTextField`, `RuleRow`/medidor y botón principal de O9), fondo de Perfil. Android: ruta `profile/password` sin barra inferior. iOS: destino de `ProfileRoute`.
- **Tests:** ViewModel con repositorio falso (habilitado, error de la actual, éxito, red).

## Apariencia (PRF-021, PRF-022)

**Paleta.** Los colores se vuelven semánticos y tienen dos valores. Los que aparecen sobre amarillo no cambian.

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `paper` | `#F1F1F1` | `#130000` | fondo de pantalla |
| `field` | `#FDFDFD` | `#221C19` | tarjetas, campo de búsqueda |
| `ink` | `#130000` | `#FDFDFD` | texto e íconos principales |
| `ink2` | `#3A3830` | `#CFCAC0` | texto secundario oscuro |
| `muted` | `#6B6650` | `#A8A496` | títulos de sección, detalles |
| `line` | `#DDD9CE` | `#4A4840` | bordes |
| `hairline` | `#E7E4DC` | `#33322D` | separadores, pista de barras |
| `off` | `#DAD6CA` | `#4A4840` | interruptor apagado |
| `inkFill` | `#130000` | `#2B2421` | rellenos oscuros: barra inferior, elegidos, "GRATIS", tarjeta de ayuda |
| `inkLine` | `#33322D` | `#3D3530` | separadores sobre `inkFill` |
| `inkMuted` | `#8A8676` | `#8A8676` | íconos inactivos sobre `inkFill` |
| `yellow`, `danger`, `readingDot`, portadas | igual | igual | |
| `onYellow`, `onYellowMuted` | `#130000`, `#3A3115` | igual | texto sobre amarillo |
| `onInkFill`, `onInkFillMuted` | `#FDFDFD`, `#DDD9CE` | igual | texto sobre `inkFill` (preguntas de ayuda, autor en "Seguir leyendo"). Agregado en K-205 |
| `card` | `#FFFFFF` | `#221C19` (`field`) | tarjeta "Nada en lectura" de Inicio. Agregado en K-205 |

Donde hoy se usa `Ink` como relleno (no como texto), pasa a `inkFill`. Se revisan una por una las pantallas de Inicio, Buscar, Estantes, el menú de libro, avisos y Perfil, con capturas en los dos modos.

**Android.** `AppearancePreference` en DataStore (clave `appearance`: `LIGHT`, `DARK`, `SYSTEM`; por defecto `SYSTEM`). `MarginColors` pasa a leer una `MarginPalette` de un `CompositionLocal` (`LocalMarginPalette`), que la raíz provee según la preferencia e `isSystemInDarkTheme()`. Los usos fuera de composición (lambdas de dibujo) leen el valor antes. Onboarding y lector se envuelven con la paleta clara. `MainActivity` vuelve a llamar `enableEdgeToEdge` con `SystemBarStyle` claro u oscuro según el modo efectivo (resuelve K-200).

**iOS.** `UserDefaults` (`appearance`). `MarginColors` pasa a colores dinámicos (claro y oscuro). La raíz aplica `.preferredColorScheme` según la preferencia (nil para Sistema) en lugar del `.light` fijo. El onboarding y el lector fuerzan claro con `.environment(\.colorScheme, .light)` o equivalente, y se verifica que los colores dinámicos lo respeten.

**Tests:** preferencia (lectura, escritura, valor por defecto), modo efectivo (función pura: preferencia + sistema → claro/oscuro), y en Android un test de Compose que compruebe la paleta provista.

## Sincronizar solo con Wi-Fi (PRF-023)

- **Dominio:** `SyncPolicy` (o `NetworkGate`): `canSync(wifiOnly, network)` pura con test. Las pasadas de biblioteca, el envío de posiciones, el borrado en la nube y las escuchas consultan la puerta antes de correr; si no pueden, el estado de SYN-008 pasa a `WaitingForWifi` y no se marca error.
- **Red:** Android `ConnectivityManager` con `NetworkCallback` (`TRANSPORT_WIFI` o `TRANSPORT_ETHERNET`); iOS `NWPathMonitor` (`usesInterfaceType(.wifi)` o `.wiredEthernet`). Un flujo de "red apta" que, al pasar a apta con la app a la vista, pide una pasada.
- **Preferencia:** DataStore `sync_wifi_only` / `UserDefaults` `sync.wifiOnly`, apagada por defecto. El interruptor de Perfil la cambia y muestra su estado real. Se suman a la lista de claves que se conservan o se borran al cerrar sesión (es del dispositivo: se conserva).
- **Abrir un libro solo en la nube** sin red apta: aviso de PRF-023, sin descarga.
- **Tests:** puerta, pasada que no corre sin Wi-Fi y corre al llegar, estado "Esperando Wi-Fi", apagar el interruptor sincroniza.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| El tema oscuro toca casi todas las pantallas; algo queda claro u oscuro sin querer | Tokens semánticos y capturas de cada pantalla en los dos modos en el PR |
| Los colores dinámicos de iOS no respetan el claro forzado del onboarding | Verificar al principio de K-206; si no, pasar la paleta por `Environment` |
| Reautenticar falla con cuentas viejas o con Google | La fila solo existe para cuentas con contraseña (PRF-008) |
| Solo Wi-Fi deja el progreso sin subir mucho tiempo | Es lo que eligió el usuario; el estado lo muestra en la biblioteca |
| Seis PR sobre la misma base tocan `KANBAN.md`, `strings.xml` y `ProfileScreen` | Cambios acotados por tarea; conflictos menores al fusionar, se resuelven en cada PR |
