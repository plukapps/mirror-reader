# Plan — Uso sin cuenta, Android

**Objetivo:** que la app de Android se use sin registrarse, solo en el dispositivo, con la cuenta como opción para sincronizar. Fiel al diseño "5a — sin cuenta" de `design/Margin Ebook App.dc.html`, con las diferencias del spec.

**Specs:** `13-no-account.md` (GST-001 a GST-022), cambios en ACC-001, ONB-001, ONB-002, ONB-003, ONB-009 (retirado), ONB-019 (reemplazado), PRF-005 y PRF-009. **Decisión técnica:** ADR 0019.
**Rama:** `feature/no-account`, desde `develop`. PR contra `develop`. **Tarjetas:** K-338 a K-347. iPhone después, con su propio plan (K-348).
**Pedido del usuario (2026-10-15):** empezar a implementar el uso sin cuenta en Android, desde `develop`.

## Punto de partida (revisado en el código)

- `startDestination(user)` (`domain/onboarding/Onboarding.kt`) decide el arranque solo con la sesión. `MainViewModel.start` lo calcula una vez por arranque.
- La sincronización ya no hace nada sin sesión: `LibrarySync`, `PositionSync`, `NoteSync`, `RemovalSync`, `LibraryWatcher` y las clases de Firebase se cuidan del usuario nulo.
- Ninguna tabla ni clave de DataStore lleva el uid. Las subidas se guían por marcas de pendiente en cada fila, así que lo hecho sin cuenta sube solo en la primera pasada con sesión (GST-012, ADR 0019).
- La línea de estado de la Biblioteca ya exige sesión (`showSyncStatus`), y `planOf(null)` ya es Free (GST-007).
- **Riesgo revisado:** quitar un libro escribe `RemovedBookEntity`, pero con `cloudDeleted = true` si el libro nunca se subió, y sin sesión ninguno se sube. La primera pasada con sesión no borra nada de la nube y el libro vuelve si la cuenta lo tiene (GST-020). K-341 lo deja cubierto con un test, sin cambiar la lógica.
- **Otro desvío:** `quotaBlock` muestra la hoja de archivo muy grande aunque no haya cuota (compara con el máximo de Free). Sin sesión no debe aparecer (GST-006).

## Tareas

Cada tarea: tarjeta a "En curso", test primero (JVM con fakes cuando se pueda; Compose en emulador), código, `./gradlew :app:testDebugUnitTest` en verde, tarjeta a "Revisión" y un commit que nombra la tarjeta.

### K-338 — Spec, ADR y plan
- `13-no-account.md`, ADR 0019, este plan, referencias en `05`, `09`, `11`, README y open-questions, y las tarjetas.

### K-339 — Arranque y bienvenida sin cuenta (GST-001 a GST-004)
- Dominio: `WelcomePreference` (`seen: Flow<Boolean>`, `markSeen()`). `startDestination(user, welcomeSeen)`: con sesión, igual que hoy; sin sesión, `Home` si se vio la bienvenida y `Welcome` si no.
- Datos: clave `welcome_seen` en `reader_settings`. No entra en `RETAINED_KEYS`, así que cerrar sesión la borra (GST-016).
- `MainViewModel.start` combina la sesión con la preferencia.
- `WelcomeScreen` según O1 v2: "Empezar a leer" como botón principal, "¿Querés sincronizar?" con los enlaces "Crear cuenta" · "Iniciar sesión", y la bajada nueva en `welcome_body`.
- Tests: `StartTest` (sin sesión y bienvenida vista → Home; con sesión ignora la preferencia), `LocalDataWiperTest` (la clave se borra), `WelcomeScreenTest` (textos, nombres accesibles y los tres destinos).

### K-340 — "Listo" sin cuenta (GST-019)
- Pantalla `GuestReadyScreen` (L0) y su ruta. "Empezar a leer" va a Inicio con `startOver(HOME)`. "Importar mis libros" hace lo mismo que O6: `pendingImport`.
- "Empezar a leer" de la bienvenida marca la bienvenida como vista y abre L0.
- Tests: `GuestReadyScreenTest` (textos y botones) y el recorrido bienvenida → L0 → Inicio.

### K-341 — Quitar sin cuenta (GST-008, GST-020)
- `BookActions.remove` / `BookEditor`: sin sesión no queda borrado pendiente para la nube (ya pasa: el libro nunca se subió). Test que lo fija.
- Texto de la confirmación según haya sesión. Sin sesión: "Se borra el libro de este dispositivo, con su progreso y sus notas."
- FAQ "¿Cómo quito un libro?" y "¿Dónde están mis libros?" con el caso sin cuenta (GST-017).
- Tests JVM: sin sesión no queda borrado pendiente, y al aparecer la sesión `RemovalSync` no borra nada; con sesión, igual que hoy.

### Checkpoint 1 (después de K-339 a K-341)
- Tests JVM en verde. Instalar en el teléfono y recorrer: bienvenida → L0 → importar → leer → cerrar la app → reabrir en Inicio. Revisar con el usuario.

### K-342 — Sin sincronización visible ni cuota (GST-006)
- `ImportQuotaCheck`: sin sesión no hay hoja (tampoco la de archivo muy grande).
- Aviso del 80 % (PLN-011): solo con sesión.
- Revisar que "Esperando Wi-Fi" no se vea sin sesión. Ya lo cubre `showSyncStatus`; agregar un test.
- Tests: `ImportQuotaCheckTest` y `LibraryViewModelTest` sin sesión.

### K-343 — Aviso "solo en este dispositivo" en Inicio y Biblioteca (GST-010)
- Dominio: `GuestNoticePreference` (descartado por dispositivo, se borra al cerrar sesión). Regla: sin sesión, con al menos un libro y no descartado.
- Generalizar el `UsageNotice` privado de `LibraryScreen` en un componente común (texto, enlace, cruz) y usarlo en la Biblioteca (L1) y en Inicio, debajo del saludo (H0). `HomeViewModel` pasa a depender de la sesión y de la preferencia.
- "Crear cuenta" descarta el aviso y abre el alta corta (K-345). Hasta K-345 abre el alta completa.
- Tests: regla en JVM, `LibraryViewModelTest`, `HomeViewModel`, y Compose en emulador (se muestra, se cierra y no vuelve en ninguna de las dos).

### K-344 — Perfil sin cuenta (GST-009, GST-018)
- `ProfileUiState.signedIn`. Sin sesión: tarjeta "Sin cuenta" con el logo; la sección "Cuenta" solo con "Ya tengo una cuenta"; sin espacio, contraseña, cerrar sesión ni eliminar cuenta; sin "Sincronizar solo con Wi-Fi". Sin sesión no se pide la cuota.
- Tocar la tarjeta abre el alta corta (K-345); "Ya tengo una cuenta" abre O7.
- Tests: `ProfileViewModelTest` (sin sesión no lee la cuota) y `ProfileScreenTest` (filas visibles y ocultas, nombres accesibles).

### Checkpoint 2 (después de K-342 a K-344)
- Tests JVM en verde, Compose de Perfil, Biblioteca e Inicio en el emulador. Recorrido a mano sin cuenta en el teléfono y la tablet (T05).

### K-345 — Quitar los intereses del alta (ONB-003, ONB-009)
- Sacar la ruta `INTERESTS`, su pantalla, su ViewModel, sus strings y el guardado de intereses. Verificar y Google van a la meta diaria (O5). Pasos "1 de 3" a "3 de 3".
- Tests: `OnboardingViewModelsTest` y `OnboardingRulesTest` actualizados; los de intereses se borran junto con la pantalla.

### K-346 — Alta corta e ingreso desde adentro (GST-011, GST-021, GST-022)
- Modo "desde adentro" en la ruta del alta (`signup?inApp=true`): cruz en lugar de flecha y "Paso 1 de 2". Verificar con "Paso 2 de 2". Al terminar va a B3, no a O5.
- Pantalla B3 (`AllSetShortScreen`) con el nombre y la cantidad de libros locales. "Volver a la biblioteca" abre la Biblioteca y limpia la pila.
- Ingreso abierto desde Perfil: al entrar, a la Biblioteca (o a O3 si falta verificar).
- La cruz de B1 vuelve a donde estaba.
- Tests: ViewModels de alta y verificación en modo corto (destino B3, sin O5), B3 en Compose (con y sin libros), y que el ingreso desde Perfil termina en la Biblioteca.

### K-347 — Cierre de docs y verificación a mano
- `specs/platforms/android.md` y AGENTS.md: cómo volver a ver la bienvenida (cerrar sesión, o borrar los datos de la app si no hay cuenta).
- Verificación en el SM-S711B y la SM-X510, a mano y sin correr instrumentados en el teléfono sin preguntar:
  - instalación nueva sin cuenta: L0, importar, leer, notas, quitar;
  - el aviso en Inicio y en la Biblioteca;
  - Perfil sin cuenta;
  - crear la cuenta desde Perfil (B1 → O3 → B3) y ver los libros en la tablet;
  - iniciar sesión en una cuenta que ya tiene libros (la unión, y que un libro quitado sin cuenta vuelve);
  - cerrar sesión → bienvenida;
  - actualizar una instalación con sesión → Inicio sin cambios.

## Criterios de terminado
- Todos los GST de Android con al menos un test que los nombra.
- `./gradlew :app:testDebugUnitTest` en verde y los instrumentados de las pantallas tocadas en verde en el emulador.
- Quien tenía sesión no nota cambios (GST-004).

## Riesgos
| Riesgo | Impacto | Mitigación |
|---|---|---|
| Un borrado hecho sin cuenta se aplica a la nube de la cuenta | Alto: pérdida de datos | K-341, temprano, con test de `RemovalSync` |
| La hoja de archivo muy grande u otro aviso de cuota aparece sin cuenta | Medio | K-342 con tests por cada aviso |
| Con la sesión, el arranque decidido una vez por inicio deja rutas viejas | Bajo | Al crear la cuenta o iniciar sesión desde adentro se navega explícitamente (K-346); cerrar sesión ya hace `startOver(WELCOME)` |
| Los libros locales se suben a una cuenta ajena si alguien inicia sesión con otra cuenta | Bajo, aceptado | Es lo que pide GST-012 (ADR 0019) |
