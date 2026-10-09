# Plan — Sincronización automática de libros (Android)

**Objetivo:** quitar el botón "Subir a la nube". Con sesión, la biblioteca se sincroniza sola: al abrir la app, al iniciar sesión y al importar. Sube los libros pendientes, trae los de la nube y descarga los que faltan en el dispositivo.

**Specs que implementa:** SYN-001, SYN-008 (`specs/product/04-sync.md`), LIB-007, LIB-009 (`specs/product/01-library.md`).
**Rama:** `feature/auto-sync`. **Tarjeta:** K-058.

## Decisiones

- **Un solo caso de uso** (`SyncLibraryUseCase`) encadena lo que ya existe: listar la nube, subir pendientes, portadas, descargar faltantes. Orden fijo; cada paso es seguro de repetir.
- **Un coordinador de aplicación** (`LibrarySync`, singleton con su propio scope) sobrevive a las pantallas. Una cola con un solo lugar (`CONFLATED`) evita corridas en paralelo: si se pide sincronizar durante una corrida, corre una más al terminar.
- **Reintento solo dentro de la app** (al abrir, al iniciar sesión, al importar y al tocar el aviso de error). Sin WorkManager todavía: se evalúa con la sincronización de posición y notas.
- **Descarga automática de todos los libros** de la nube. Cambia LIB-007 ("se descarga al abrirlo"). Abrir un libro sigue descargándolo si aún no está.
- **Estado visible (SYN-008)** en la biblioteca: sincronizando, pendiente, con error. Reemplaza al botón y a los avisos de subida.
- **Wi-Fi solo (SYN-009)** queda fuera: tarjeta K-081. Mientras tanto la descarga usa también datos móviles.

## Tareas

### Tarea 1: specs (S)
- [x] LIB-007 y SYN-001 al día con la descarga y subida automáticas.
**Verificación:** revisión de los specs.

### Tarea 2: `SyncLibraryUseCase` (M)
- [x] `CloudBooksRepository.cloudOnlyBookIds()` (consulta en Room).
- [x] Caso de uso: listar nube, subir, portadas, descargar faltantes; informa subidos, sin espacio, fallidos, descargados y si no hubo conexión; sin conexión corta la corrida.
- [x] Tests JVM con fakes (SYN-001, SYN-008, LIB-007, LIB-009), verificados por mutación.

### Tarea 3: coordinador `LibrarySync` (M)
- [x] Estado `SyncState` (corriendo, problema de la última corrida). Pedidos concurrentes no duplican trabajo; sin sesión no corre.
- [x] Módulo de Hilt con el scope de aplicación.
- [x] `MainViewModel.syncCloudBooks()` lo arranca; `LibraryViewModel.onImport` pide sincronizar al terminar.
- [x] Tests JVM.

### Tarea 4: interfaz (S)
- [x] Se quitan `UploadBar`, `onUpload`, `uploading` y los avisos de subida.
- [x] Aviso de estado en la biblioteca; tocarlo con un error reintenta.
- [x] Tests de ViewModel actualizados.

### Tarea 5: verificación (S)
- [ ] Tests JVM completos y compilación.
- [ ] A mano en el teléfono: importar sube solo; dispositivo nuevo baja todo; sin conexión queda pendiente y se completa al reabrir.

### Tarea 6: sincronizar al volver a la app, con TTL (S) — K-083
- [x] `LibrarySync.requestIfStale()`: el consumidor corre la pasada solo si hubo un pedido explícito o la última pasada exitosa fue hace más de 5 minutos. El reloj es monotónico e inyectable para los tests.
- [x] Una pasada sin conexión o con excepción no consume el TTL. Un pedido explícito no se pierde si llega un pedido por antigüedad después.
- [x] `MainActivity.onStart` lo pide (cubre volver a primer plano y recrear la actividad).
- [x] Tests con reloj falso (SYN-001).

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Descargar todo por datos móviles | Medio | K-081 (SYN-009). Cuota acotada a 100 MiB en el entorno de prueba. |
| Abrir un libro mientras la sincronización lo baja | Bajo | El importador acepta el libro ya instalado (`AlreadyInLibrary`); se baja dos veces como mucho. |
| Una lectura de Firestore por cada arranque | Bajo | Una sola corrida por arranque y por pedido. |
