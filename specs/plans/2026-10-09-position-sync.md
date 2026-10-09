# Plan — Sincronización de la posición de lectura

**Objetivo:** que la posición y el porcentaje de cada libro sigan al lector entre dispositivos de la forma más rápida y barata posible, sin esperar a la sincronización de la biblioteca (5 minutos). Es la misión central del producto: continuar una lectura en otro dispositivo.

**Specs que implementa:** SYN-002 (posición), SYN-003, SYN-011, SYN-012, SYN-013 (`specs/product/04-sync.md`), RDR-006.
**Rama:** `feature/position-sync`. **Depende de:** K-058 (sincronización de libros, ya en `master`). **Plataforma:** Android; el modelo de datos sirve también a Apple.

## Decisiones (tomadas con el usuario, 2026-10-09)

- **Subir:** el dispositivo guarda local al instante (ya existe, `ReaderViewModel.savePositionWhileReading`, 250 ms). El envío a la nube sale **solo al cerrar el libro**: al salir del lector o cuando la app pasa a segundo plano. Un envío por sesión de lectura. Sin temporizador mientras se lee (decisión del usuario, 2026-10-09). Lo que no se pudo enviar (sin conexión, proceso muerto con el libro abierto) queda marcado como pendiente y sale en el próximo arranque o al volver la conexión.
- **Bajar:** un **listener en vivo mientras la app está a la vista** (se corta al salir; sigue sin haber trabajo en segundo plano) más una **lectura puntual al abrir el libro**. No es polling cada minuto: Firestore solo manda lo que cambió.
- **Al abrir un libro** con posición remota más nueva: diferencia de hasta 2 % del libro, salta sola; mayor, pregunta (SYN-003).
- **Mientras se lee:** si otro dispositivo avanza el mismo libro, aviso discreto "Seguir desde [dispositivo]". La página no se mueve sola (SYN-013).
- **Orden entre dispositivos:** gana la lectura más reciente por `readAt` (hora del dispositivo en que se leyó). `updatedAt` es la hora del servidor y solo sirve para pedir "lo que cambió desde la última vez". Se desvía a propósito del principio "marcas de tiempo del servidor" de `backend.md`: con la hora del servidor un dispositivo que estuvo sin conexión un día pisaría una lectura más nueva. Se registra en el ADR 0011.

## Modelo en la nube

`users/{uid}/positions/{bookId}` (mismo `bookId` que el libro):

| Campo | Tipo | Notas |
|---|---|---|
| `locatorJson` | string | Locator serializado, ≤ 8 KiB. |
| `progress` | número 0..1 o null | Progresión total, para los porcentajes. |
| `readAt` | número (ms) | Hora del dispositivo en que se leyó. |
| `deviceId` | string ≤ 64 | Identifica la instalación (para no reaplicar lo propio). |
| `deviceName` | string ≤ 64 | Nombre legible para el aviso ("SM-X510"). |
| `updatedAt` | timestamp | Del servidor; permite pedir solo lo cambiado. |

Reglas: solo el dueño; crear y actualizar validan tipos y tamaños; `updatedAt == request.time`; **`readAt` nuevo ≥ `readAt` guardado** (un dispositivo atrasado no pisa uno más nuevo); sin borrado desde el cliente.

## Tareas

### Tarea 1: requisitos, modelo y ADR (S) — K-085
- [x] SYN-003, SYN-011, SYN-012, SYN-013 en `specs/product/04-sync.md`.
- [x] Modelo y reglas en `specs/platforms/backend.md` (quitar posición de "Fuera de este modelo").
- [x] ADR 0011: listener frente a polling, `readAt` frente a hora del servidor, cuándo se envía.
**Verificación:** revisión de los specs.

### Tarea 2: backend, reglas y tests (M) — K-086
- [x] Reglas de Firestore para `users/{uid}/positions/{bookId}` y tests en el emulador: aislamiento entre usuarios, validación de campos y tamaños, `readAt` que no retrocede, `updatedAt` del servidor, sin borrado.
- [x] Verificación por mutación (quitar una validación y ver el test fallar).
**Depende de:** Tarea 1. **Desplegar las reglas al proyecto real es una acción del usuario.**

### Tarea 3: modelo local (M) — K-087
- [x] Room v5 (`MIGRATION_4_5`): en `reading_positions`, `isSynced` (por defecto 0: las posiciones existentes se envían una vez). Sin columna `deviceName`: el nombre del origen llega con la lectura de la nube. Test de migración (escrito y compilado, sin correrlo en dispositivo).
- [x] Identificador de instalación (`deviceId`) en DataStore, generado una vez. `lastPositionsSeenAt` (mayor `updatedAt` visto) también en DataStore.
- [x] Regla pura `mergePosition(local, remote)` en `domain`, con tests: gana `readAt` mayor; empate gana el local; lo propio (`deviceId` igual) se ignora; local pendiente más nuevo no se pisa (SYN-003, SYN-010).
**Depende de:** Tarea 1.

### Tarea 4: envío y recepción (L) — K-088
- [x] Interfaz `RemotePositions` en `domain/remote` (`push`, `fetch(bookId)`, `observeChangesSince(updatedAt)`), con implementación Firestore en `data/remote` detrás de `remoteCall`.
- [x] `PositionSync` (singleton, scope de aplicación; también expone `remoteUpdates` para el aviso de la Tarea 6): `flush()` envía las posiciones pendientes; lo llaman el cierre del lector y `MainActivity.onStop`, y también el arranque de la app y `LibrarySync` al terminar una pasada exitosa (para reenviar lo pendiente). Sin sesión o sin conexión queda pendiente.
- [x] Listener mientras la app está a la vista: `MainActivity.onStart/onStop` → `PositionSync.startListening()/stopListening()` (sin dependencia nueva). Pide `updatedAt > lastPositionsSeenAt`; aplica `mergePosition` y actualiza Room, de modo que Inicio y la biblioteca (que ya observan `observeProgress`) cambian solos (SYN-012).
- [x] `ReaderViewModel.onCleared` llama a `flush()` (cerrar el libro); `MainActivity.onStop` también (app a segundo plano).
- [x] Tests JVM con fakes (SYN-011, SYN-012): pasar páginas no envía nada; `flush` envía una vez; pendiente sin conexión y reenvío al volver; un proceso nuevo reenvía lo pendiente; recepción que no pisa lo local más nuevo.
**Depende de:** Tareas 2 y 3.

### Tarea 5: abrir el libro con la posición más reciente (M) — K-089
- [x] Al abrir (`ReaderViewModel.open`), lectura puntual `fetch(bookId)` con espera máxima de ~2 s; sin conexión o sin documento se sigue con lo local (nunca se bloquea la lectura).
- [x] Diferencia ≤ 2 % del libro: se abre en la posición más reciente sin preguntar. Mayor: pregunta "¿Continuar desde donde quedaste en [dispositivo]?" con "Continuar" y "Quedarme aquí" (SYN-003).
- [x] Textos en español en `strings.xml`. Tests de `ReaderViewModel` (umbral, tiempo de espera, sin conexión).
**Depende de:** Tarea 4.

### Tarea 6: aviso mientras se lee (S) — K-090
- [x] Si llega una posición remota más nueva del libro abierto, chip discreto "Seguir desde [dispositivo]"; tocarlo navega al locator; se descarta solo si el lector avanza más allá (SYN-013). La página nunca se mueve sola.
- [x] La API de Readium para navegar a un locator se verifica con `javap` o la documentación, no de memoria.
**Depende de:** Tarea 4.

### Tarea 7: verificación en dos dispositivos (S) — K-091
- [ ] Reinstalar en teléfono y tablet. Leer en uno, abrir el mismo libro en el otro: salta o pregunta según la distancia.
- [ ] Con los dos abiertos en el mismo libro: aparece el aviso; la página no se mueve sola.
- [ ] Porcentajes de Inicio y de la biblioteca se mueven solos con la app a la vista.
- [ ] Sin conexión en uno: queda pendiente y se envía al volver, sin pisar una lectura más nueva del otro.
- [ ] Revisar `RemoteRequest` en el log: una escritura por sesión de lectura y ninguna mientras se pasan páginas; sin lecturas repetidas.
**Depende de:** todas.

## Costos (Firestore)

- Escrituras: una por sesión de lectura (al cerrar el libro o pasar a segundo plano). Una sesión larga cuesta lo mismo que una corta.
- Lecturas: el listener pide `updatedAt` mayor al último visto, así que tras la primera carga solo llegan los cambios. La lectura al abrir es un documento.

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Relojes desfasados entre dispositivos alteran quién "lee más reciente" | Medio | Ordenar por `readAt`, que es la hora de lectura; diferencias de segundos no cambian la experiencia. Anotado en el ADR 0011. |
| El formato del locator difiere entre Android (Readium Kotlin) y Apple | Medio | Ambos usan el JSON de locator de Readium; verificarlo al llevarlo a Apple (K-079). |
| La caché de Firestore está desactivada (ADR 0007): el listener recarga todo en cada conexión | Bajo | El filtro por `updatedAt` acota la recarga a lo cambiado desde `lastPositionsSeenAt`. |
| El listener consume batería o datos con la app a la vista | Bajo | Solo mientras la actividad está a la vista; el SDK ya agrupa la conexión. |
| El otro dispositivo ve el avance recién cuando el primero cierra el libro (leer en los dos a la vez no se refleja en vivo) | Bajo | Decisión del usuario por simplicidad y costo. Si molestara, se aplica la técnica de K-092 (envío por inactividad con tope) sin cambiar el resto. |
| Se pierde el envío si el proceso muere con el libro abierto | Bajo | La posición queda guardada local y marcada como pendiente; sale en el próximo arranque. Solo se demora. |
| Aviso o diálogo molesto si los dispositivos se alternan seguido | Bajo | Umbral del 2 % y aviso sin mover la página. Se ajusta con uso real. |
| Reglas sin desplegar: el envío falla | Medio | Se ignora el rechazo y queda pendiente; la tarea 7 exige haber desplegado antes. |

## Deuda técnica: envío por inactividad (K-092)

Decisión del usuario (2026-10-09): el plan envía solo al cerrar el libro o pasar a segundo plano (opción A). La opción B queda registrada para usarla solo si se encuentran problemas, por ejemplo que alguien deje el libro abierto con la pantalla encendida y el otro dispositivo no vea su avance.

**Técnica:** además del envío al cerrar, `PositionSync` envía cuando pasan **60 s sin cambiar de página** y, si se lee de corrido, **como máximo cada 5 min**. Es un `debounce` con espera máxima sobre las posiciones pendientes; el resto del plan no cambia.

- Costo: unas pocas escrituras por sesión; leyendo 1 hora de corrido, 12 como máximo. El costo en dinero es despreciable (menos de una milésima de dólar por usuario y hora incluso enviando cada 10 s); lo que se cuida es la batería, los datos y la simplicidad.
- Cambia SYN-011 (hoy "al cerrar el libro") y requiere tests con reloj falso: ráfaga de páginas sin pausa, pausa de 60 s, tope de 5 min y pendiente sin conexión.
- Descartada por excesiva: enviar además por avance (cada 2 % o cambio de capítulo).

## Fuera de este plan

Marcadores, subrayados y notas (K-009), preferencias de lectura entre dispositivos, mover la página en vivo, sincronización con la app cerrada (WorkManager), Wi-Fi solo (K-081), llevarlo a Apple.
