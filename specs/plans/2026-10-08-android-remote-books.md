# Android — Libros remotos (primera rebanada de sync)

**Objetivo:** que la app Android suba sus libros a la nube y los baje en otro dispositivo, contra el backend de Firebase ya desplegado (`mirror-reading-staging`). Primera parte de K-010.

**Specs que implementa:** LIB-007 (solo en la nube / descargado), LIB-009 (cuota bloquea nuevas subidas, nunca la lectura), ACC-003 (espacio usado y disponible), ACC-004 (solo lo propio), SYN-001 y SYN-008 en su versión mínima (local primero, estado visible).
**Decisiones técnicas:** ADR 0007 (Firebase), ADR 0005 (SDK de Firebase en la app, sin Retrofit), ADR 0002 (offline-first), ADR 0008 (cuota). Modelo de datos remoto: `specs/platforms/backend.md`.

## Alcance acordado

- **Entra:** subir EPUB a la nube, listar los libros remotos, descargarlos al abrirlos, mostrar espacio usado y libre.
- **Cuenta hardcodeada.** No hay pantallas de registro ni de login. La app inicia sesión sola con una cuenta de desarrollo (solo en builds debug).
- **No entra:** pantallas de cuenta (K-010 siguiente), Google Sign-In, eliminar de la biblioteca ni quitar la descarga (LIB-008), colecciones, posición, anotaciones y preferencias, transferir solo con Wi-Fi (SYN-009), reintentos en segundo plano (SYN-008 completo), portadas remotas.

## Decisiones

- **SDK de Firebase** (BoM) para Auth, Firestore y Storage, detrás de interfaces de `domain`. Room sigue siendo la fuente de verdad. El caché local de Firestore se desactiva.
- **Mismo identificador:** el `id` local de un libro (SHA-256 del contenido) es el `bookId` remoto y el nombre del archivo en Storage.
- **Cuenta de desarrollo sin credenciales en el repo** (regla de AGENTS.md). Email y clave viven en `local.properties` (ignorado por git) y llegan a la app por `BuildConfig`, solo en el tipo de build `debug`. `google-services.json` también va ignorado.
- **Proyecto real, no emulador:** el build debug apunta a `mirror-reading-staging`. Usar el emulador local desde el teléfono queda para más adelante.
- **Orden de subida:** primero el archivo (la regla de Storage valida tipo, tamaño y cuota), después el documento de metadatos. Reintentar es seguro: el archivo ya existe y el documento se actualiza.
- **Portadas:** no se sincronizan. Un libro solo en la nube muestra una portada genérica hasta descargarse; al descargar se extrae la portada con la misma lógica de la importación.
- **Cuándo se sube:** con un botón en la biblioteca ("Subir a la nube"), que sube todos los libros importados en la app que aún no estén subidos. La subida automática (al importar y al abrir la app) viene después, en su propia tarjeta. Sin segundo plano ni reintentos todavía.

## Preguntas resueltas (2026-10-08)

1. **Cuenta de desarrollo:** el usuario ya está creado en Authentication. Sus credenciales se guardan solo en `local.properties` (no en el repo ni en este plan).
2. **Subida:** primero con botón en la biblioteca; automática después.
3. **Qué se sube:** los libros importados en la app (los que están en Room), no los demás archivos del teléfono.

## Tareas

### Tarea 1: Firebase en la app (S) — K-051
- [ ] Plugin de google-services, BoM de Firebase y dependencias de Auth, Firestore y Storage. Versiones verificadas contra la documentación oficial (`source-driven-development`).
- [ ] `google-services.json` descargado de la consola y guardado en `code/android/app/` (ignorado por git). README con cómo obtenerlo.
- [ ] Permiso `INTERNET` si falta. Caché local de Firestore desactivado.
- [ ] Módulo de Hilt que provee las instancias de Firebase.
**Verificación:** compila, los tests JVM existentes siguen pasando, la app abre en el teléfono sin cerrarse.
**Depende de:** el proyecto Android registrado en Firebase (ya está) y `google-services.json` descargado de la consola.

### Tarea 2: cuenta de desarrollo (S) — K-052
- [ ] `domain`: interfaz `AccountRepository` (usuario actual como `Flow`, quién es). Sin Firebase.
- [ ] `data`: implementación con Firebase Auth que inicia sesión con las credenciales de `BuildConfig` al arrancar. Si faltan, queda sin sesión y la app sigue funcionando local (ADR 0002).
- [ ] `local.properties.example` documentado; `BuildConfig` solo en debug.
- [ ] Tests JVM con repositorio falso: sin credenciales no hay sesión y no se rompe nada.
**Verificación:** en el teléfono, el log muestra el `uid` y existe `users/{uid}` en Firestore con plan `free` (ACC-001, ACC-004).
**Depende de:** Tarea 1.

### Tarea 3: modelo local de libros remotos (M) — K-053
- [ ] `domain`: `LibraryBook` pasa a saber si el archivo está en este dispositivo y si está subido (LIB-007). Interfaz `RemoteLibrary` (listar, subir metadatos) y `BookFileStore` (subir y bajar archivo).
- [ ] Room versión 3: columnas nuevas en `books` (subido y descargado) con migración probada. Un libro solo en la nube es una fila sin archivo local.
- [ ] Mapeo entre `BookEntity` y el documento remoto (`authors` es lista, el local tiene un autor opcional).
- [ ] Tests: migración 2→3 conserva los libros; mapeos.
**Verificación:** tests JVM y de migración en emulador. Pedir antes de correr tests instrumentados (la app se prueba en el teléfono).
**Depende de:** Tarea 1.

### Checkpoint: modelo listo
- [ ] Compila, tests JVM y de migración pasan, el comportamiento local no cambió.
- [ ] Revisar contigo antes de seguir.

### Tarea 4: subir libros (M) — K-054
- [ ] Caso de uso `UploadBooksUseCase`: libros locales no subidos → subir archivo con `contentType` `application/epub+zip`, escribir el documento (fechas del servidor, `deletedAt` nulo), marcar como subido.
- [ ] Antes de subir, comparar `usedBytes` + tamaño con `quotaBytes`; si no entra, no se sube y se informa (LIB-009). Leer nunca se bloquea.
- [ ] Implementación con Storage y Firestore detrás de las interfaces. Errores de red: el libro queda pendiente, sin perder nada.
- [ ] Botón "Subir a la nube" en la biblioteca, con estado (subiendo, listo, error, cuota llena). Sube todos los libros importados aún no subidos.
- [ ] Tests JVM con fakes: sube lo pendiente, no repite lo ya subido, respeta la cuota, la falla de red deja pendiente (SYN-001).
**Verificación:** tests JVM; en el teléfono, un libro importado aparece en la consola de Firebase (archivo y documento) y `usedBytes` sube.
**Depende de:** Tarea 3 y Tarea 2.

### Tarea 5: listar y descargar (M) — K-055
- [ ] Al abrir la app con sesión: leer los libros remotos vivos (`deletedAt` nulo) y crear en Room los que falten como "solo en la nube".
- [ ] La grilla distingue "en la nube" y "descargado" (LIB-007), con portada genérica para los que no tienen archivo.
- [ ] Abrir un libro solo en la nube lo descarga a `books/{id}.epub` con indicador de progreso y error recuperable, extrae la portada y lo abre.
- [ ] Tests JVM con fakes: libros nuevos remotos aparecen; abrir uno remoto lo descarga una sola vez; la falla de descarga no corrompe el estado.
**Verificación:** tests JVM; en el teléfono, borrando los datos de la app y volviendo a abrirla, aparece la biblioteca completa en la nube y un libro se descarga y se lee (escenario de ACC "dispositivo nuevo").
**Depende de:** Tarea 4.

### Tarea 6: espacio usado y disponible (S) — K-056
- [ ] Leer `usedBytes` y `quotaBytes` de `users/{uid}` y mostrarlos en la biblioteca (ACC-003).
- [ ] Aviso claro al llegar a la cuota, sin bloquear la lectura (LIB-009).
- [ ] Tests JVM del formato y del estado "cuota llena".
**Verificación:** tests JVM; en el teléfono se ve el espacio y baja o sube al subir o borrar archivos desde la consola.
**Depende de:** Tarea 2 (puede ir en paralelo con 4 y 5).

### Tarea 7: cierre (S) — K-057
- [ ] `specs/platforms/android.md` y `AGENTS.md` al día (SDK de Firebase, cuenta de desarrollo, `local.properties`).
- [ ] KANBAN: K-010 queda dividida; lo que no entró (login, Google, eliminar, quitar descarga, segundo plano) con tarjeta en Backlog.
- [ ] Verificación a mano completa en el teléfono, con el recorrido de los 4 escenarios de arriba.
**Depende de:** Tareas 4, 5 y 6.

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Credenciales de la cuenta de desarrollo filtradas al repo | Alto | Solo en `local.properties` y `BuildConfig` de debug; revisar el diff antes de cada commit. |
| Contraseña débil de la cuenta de desarrollo en un proyecto real | Bajo | Es una cuenta de staging sin datos sensibles. Cambiar la contraseña o borrar la cuenta antes de abrir el proyecto a otras personas. |
| Cuota de 15 MiB deja afuera libros típicos (un EPUB suele pesar 1 a 5 MB, algunos más) | Medio | Es la cuota provisional (open-questions #2); la regla ya informa al usuario. Medir con los libros reales del teléfono. |
| Subir antes de que `users/{uid}` exista se rechaza | Medio | La cuenta de desarrollo ya existe al probar; en el futuro, esperar el documento tras el registro (ver `backend.md`). |
| Dos cachés (Room y Firestore) se desincronizan | Medio | Caché de Firestore desactivado; Room es la única fuente de verdad. |
| Descarga interrumpida deja un archivo a medias | Medio | Descargar a un temporal y mover al final (mismo patrón que `newTempFile` de la importación). |
| Cambio de esquema de Room rompe a quien ya tiene la app | Alto | Migración 2→3 con test; no se borra la base. |
| Lock-in con el SDK | Medio | Interfaces en `domain`, archivos tras `BookFileStore` (ADR 0007). |

## Fuera de este plan

Subida automática (al importar y al abrir la app), pantallas de registro e inicio de sesión, Google Sign-In, cierre de sesión y dispositivos (ACC-006), eliminar y quitar descarga (LIB-008), colecciones remotas, posición y anotaciones, Wi-Fi solo, reintentos en segundo plano, portadas remotas, proyecto de producción. El backend pendiente (K-050) se decide antes de abrir la app a usuarios reales.
