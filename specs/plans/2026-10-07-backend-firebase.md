# Backend — Firebase v1

**Objetivo:** dejar listo el backend de Firebase en `code/backend/v1`: cuentas, datos de usuario y archivos de libros, con aislamiento por usuario y cuota de espacio. Es la base de la rebanada de sincronización del cliente Android (K-010). Este plan **no** incluye código de Android.

**Specs que implementa:** `specs/product/05-account-and-plans.md` (ACC-001 a ACC-005), `specs/product/04-sync.md` (SYN-002, SYN-007 en el modelo de datos), `specs/product/01-library.md` (LIB-007 a LIB-009 en el modelo y la cuota).
**Decisión técnica:** ADR 0007 (Firebase, reemplaza al 0003).

## Decisiones

- Todo el backend es configuración y reglas: `firebase.json`, reglas de Firestore y de Storage, índices y, solo si hace falta, Cloud Functions (TypeScript). Sin servidor propio.
- Las reglas se prueban en el **emulador de Firebase** con `@firebase/rules-unit-testing`. Cada requisito tiene al menos un test que lo referencia por ID.
- Un solo proyecto de Firebase para empezar (`dev`). Producción se crea antes de publicar en tiendas, no ahora.
- Modelo de datos (se fija en la Tarea 2, aquí solo el borrador):
  - `users/{uid}` — plan, bytes usados, fecha de alta.
  - `users/{uid}/books/{bookId}` — metadatos del libro (`bookId` = hash del archivo, igual que en Android), ruta del archivo, estado, `updatedAt`, `deletedAt` (marca de borrado, SYN-007).
  - `users/{uid}/collections/{id}`, `users/{uid}/books/{bookId}/...` para posición y anotaciones (se detallan en las rebanadas de ANN y SYN).
  - Archivos: `users/{uid}/books/{sha256}.epub`.
- Marcas de tiempo las pone el servidor, no el cliente (`serverTimestamp`), para que "gana el último cambio" (SYN-006) no dependa del reloj del teléfono.
- La cuota (ACC-002, LIB-009) se cuenta en `users/{uid}.usedBytes`. Las reglas de Storage la consultan al subir. Ver Tarea 4.
- Credenciales: `google-services.json` y claves de servicio **no** van al repo. Se agregan al `.gitignore` en la Tarea 1.

## Preguntas abiertas (necesitan tu respuesta)

1. **Región de Firestore y de Storage.** No se puede cambiar después. Propuesta: `southamerica-east1` (São Paulo), la más cercana a Uruguay. Confirmar al crear el proyecto.
2. **Tamaño de la cuota gratuita** (open-questions #2). **Decidido: 15 MiB, provisional (2026-10-07).** Antes: Para las pruebas se usa un valor configurable (propuesta inicial: 100 MB por usuario). El valor final se decide aparte.
3. **Plan Blaze y alertas de presupuesto.** Cloud Storage exige Blaze. Propuesta: activar una alerta de presupuesto bajo (por ejemplo USD 5) al crear el proyecto.
4. **¿Cloud Functions?** Solo si la Tarea 4 las necesita. Funciones también requieren Blaze (ya requerido).

## Tareas

### Tarea 1: estructura y emuladores (S) — K-044
- [ ] Carpeta `code/backend/v1` con `firebase.json`, `.firebaserc` (id del proyecto `dev`), `firestore.rules`, `firestore.indexes.json`, `storage.rules`.
- [ ] Emuladores de Auth, Firestore y Storage configurados. `README.md` con los comandos.
- [ ] Proyecto Node para los tests de reglas (`package.json`, runner de tests).
- [ ] `.gitignore`: `google-services.json`, claves de servicio, `node_modules`, logs del emulador.
**Verificación:** el emulador arranca (`firebase emulators:start`) y un test de humo corre contra él.
**Depende de:** el proyecto de Firebase creado (lo creás vos) y sus datos de región. Verificar comandos contra la documentación oficial.

### Tarea 2: modelo de datos y reglas de Firestore (M) — K-045
- [ ] `specs/platforms/backend.md` con el modelo de datos definitivo (colecciones, campos, tipos, índices) y qué reglas cumple cada colección.
- [ ] `firestore.rules`: cada usuario lee y escribe solo bajo `users/{uid}` con su propio `uid` (ACC-004). Sin acceso anónimo.
- [ ] Reglas de validación de forma: campos obligatorios, tipos, `deletedAt` solo por marca (no se borra el documento, SYN-007), el cliente no puede modificar `plan` ni `usedBytes`.
- [ ] Tests: usuario A no lee ni escribe datos de B; usuario sin sesión no accede; el cliente no puede subirse la cuota.
**Verificación:** tests de reglas en el emulador (`ACC-004`, `ACC-005`, `SYN-007`).
**Depende de:** Tarea 1.

### Tarea 3: reglas de Storage (S) — K-046
- [ ] `storage.rules`: solo el dueño lee y escribe `users/{uid}/books/*` (ACC-004).
- [ ] Solo `application/epub+zip`, tamaño máximo por archivo (propuesta: 100 MB).
- [ ] Tests: ajeno no accede, sin sesión no accede, tipo o tamaño inválido se rechaza.
**Verificación:** tests de reglas de Storage en el emulador (`ACC-004`).
**Depende de:** Tarea 1.

### Tarea 4: cuota de espacio (M) — K-047
Riesgo más alto del plan: va primero entre las tareas que pueden cambiar el diseño.
**Resultado del spike:** las reglas de Storage sí pueden leer Firestore, pero el cliente no puede mantener `usedBytes` (las reglas de Firestore no ven el tamaño real del archivo). Se tomó el plan B: Cloud Function, ADR 0008.
- [ ] Spike corto: ¿las reglas de Storage pueden leer `users/{uid}.usedBytes` y la cuota con `firestore.get()`? Verificar contra la documentación oficial y probar en el emulador.
- [ ] Si sí: regla de Storage que rechaza subidas que pasen la cuota (LIB-009), y actualización de `usedBytes` por el cliente en una transacción junto al documento del libro, con regla de Firestore que verifica que coincida con el tamaño del archivo.
- [ ] Si no: Cloud Function (TypeScript) que actualiza `usedBytes` al crear o borrar un archivo, y la subida se valida en reglas con el valor actual. Registrar la decisión como ADR 0008.
- [ ] Tests: no se puede subir pasada la cuota (`LIB-009`, `ACC-002`); leer y borrar siguen permitidos con la cuota llena; borrar un libro libera espacio; `usedBytes` visible para el usuario (`ACC-003`).
**Verificación:** tests en el emulador; cuota configurable.
**Depende de:** Tareas 2 y 3.

### Checkpoint: reglas completas
- [ ] Todos los tests de reglas pasan en el emulador.
- [ ] Cada ID de requisito citado arriba tiene al menos un test que lo referencia.
- [ ] Revisar contigo antes de seguir.

### Tarea 5: cuentas (S) — K-048
- [ ] Activar en el proyecto los proveedores de Auth: email/contraseña y Google (ACC-001). Documentar los pasos manuales en el README (se hacen en la consola).
- [ ] Documento `users/{uid}` creado al registrarse, con plan gratuito (ACC-002, ACC-005). Se resuelve con una Cloud Function de `onCreate` o con una escritura del cliente validada por reglas, según lo decidido en la Tarea 4.
- [ ] El `SHA-1` de la app Android se registra para el inicio con Google. Sin claves en el repo.
- [ ] Tests con el emulador de Auth: alta de usuario crea su documento con plan gratuito y cuota en cero.
**Verificación:** tests en el emulador (`ACC-001`, `ACC-002`, `ACC-005`).
**Depende de:** Tarea 2 (y Tarea 4 si se elige la función).

### Tarea 6: despliegue al proyecto `dev` y cierre (S) — K-049
- [ ] Desplegar reglas e índices al proyecto `dev` (`firebase deploy`, con la herramienta del proyecto o la CLI).
- [ ] Revisar las reglas desplegadas contra las locales.
- [ ] Alerta de presupuesto confirmada.
- [ ] README final, ADR 0007 actualizado si algo cambió, `AGENTS.md` con los comandos del backend.
**Verificación:** las reglas desplegadas rechazan un acceso ajeno en el proyecto real (prueba manual corta); `AGENTS.md` y specs consistentes.
**Depende de:** Checkpoint y Tarea 5.

## Fuera de este plan

- Cliente Android de sincronización (K-010 se parte en un plan propio cuando esto termine): cola de cambios, resolución de conflictos (SYN), SDK de Firebase en la app, actualización del ADR 0005.
- Anotaciones y posición en el servidor: el modelo las contempla, pero sus reglas se agregan con ANN y SYN.
- Proyecto de producción, plan de pago, "Iniciar sesión con Apple".

## Riesgos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Las reglas de Storage no pueden aplicar la cuota por sí solas | Alto | Spike al inicio de la Tarea 4, con plan B de Cloud Function (ADR 0008). |
| Región elegida mal | Alto (irreversible) | Pregunta abierta 1, confirmar antes de la Tarea 1. |
| Costos por sorpresa en Blaze | Medio | Alerta de presupuesto; las descargas son una vez por dispositivo (offline-first). |
| Reglas con huecos (acceso a datos ajenos) | Alto | Tests por requisito, revisión con `security-and-hardening` en el checkpoint. |
| Dependencia de Firestore | Medio | Modelos de `domain` sin Firebase; archivos tras `BookFileStore` (ADR 0007). |
