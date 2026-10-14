# Catálogo de planes en Remote Config

**Objetivo:** que el espacio total, el tamaño máximo por archivo y el precio de cada plan (`free`, `plus`, `pro`) se configuren en Remote Config y que el backend los aplique a cada usuario. Este plan cubre el backend. Las apps (mostrar el catálogo, rechazar con el máximo del plan) van en planes propios (K-286, K-287).

**Specs que implementa:** `specs/product/05-account-and-plans.md` (ACC-007 a ACC-010; toca ACC-002 y LIB-009).
**Decisión técnica:** ADR 0017.

## Valores iniciales (provisionales, se cambian en la consola)

| Plan | Espacio | Máximo por archivo | Precio mensual / anual |
|---|---|---|---|
| `free` (Free) | 15 MiB (0,0146484375 GB, como hoy) | 2 MB (hoy 100 MB) | 0 / 0 USD |
| `plus` (Plus) | 5 GB | 50 MB | 2,99 / 24,99 USD |
| `pro` (Pro) | 50 GB | 100 MB | 5,99 / 49,99 USD |

Son los valores de PLN-001 (`specs/product/12-plans.md`), decisión del usuario (2026-10-14); los ids siguen siendo `free`, `plus` y `pro`. Nadie queda en `plus` o `pro` hasta que haya cobro. Para probar se puede cambiar `plan` a mano en la consola de Firestore.

## Tareas

### Tarea 1: spec, ADR y template (S) — K-280
- [x] ACC-007 a ACC-010, ADR 0017, `backend.md`, open-questions #1 y #2.
- [x] `remoteconfig.template.json` y entrada `remoteconfig` en `firebase.json`.
**Verificación:** revisión del usuario.

### Tarea 2: lectura y validación del catálogo (S) — K-281
- [x] `functions/src/plans.ts`: `parsePlans(json)` devuelve el catálogo o `null` si es inválido (falta `free`, valores no numéricos o negativos, moneda vacía); `planLimits(plan)` pasa a bytes (`floor(storageGb × 1024³)`, `floor(maxFileMb × 1024²)`); `DEFAULT_PLANS` igual al template del repo.
- [x] `loadPlans()`: lee el template con el Admin SDK, toma solo el valor por defecto de `plans`, cae en `DEFAULT_PLANS` si falla o es inválido y lo registra.
- [x] Test que compara `DEFAULT_PLANS` con `remoteconfig.template.json`, para que no se separen.
**Verificación:** tests de Node (`functions/tests/plans.test.mjs`) con `ACC-007` y `ACC-008` en el nombre.

### Tarea 3: alta con los límites del plan (S) — K-282
- [x] `createUserDoc` recibe el catálogo y escribe `plan: "free"`, `quotaBytes` y `maxFileBytes` de `free`. Sale `FREE_PLAN`.
- [x] `onUserCreated` llama a `loadPlans()`.
**Verificación:** tests de `accounts.test.mjs` actualizados (ACC-002, ACC-007) en el emulador.

### Tarea 4: aplicar cambios del catálogo (M) — K-283
- [x] `applyPlans(db, plans)`: recorre `users` (solo `plan`, `quotaBytes` y `maxFileBytes`) y actualiza en lotes los que tienen `quotaBytes` o `maxFileBytes` distintos. No toca `usedBytes` ni nada más. Devuelve cuántos cambió.
- [x] `onPlansUpdated` (`onConfigUpdated`): `loadPlans()` sin caer en los valores por defecto (si el template es inválido, no aplica nada) y `applyPlans`.
- [x] Tests: cambia solo los usuarios del plan modificado; bajar la cuota por debajo de `usedBytes` no borra nada; repetir no cambia nada; catálogo inválido no toca a nadie; un usuario con un plan que no existe en el catálogo queda igual (y se registra).
**Verificación:** tests en el emulador (ACC-009).

### Tarea 5: regla de Storage con el máximo del plan (S) — K-284
- [x] `storage.rules`: `request.resource.size <= user.maxFileBytes` en lugar de `100 * 1024 * 1024`, leyendo el documento una sola vez para cuota y máximo.
- [x] Tests: dentro del máximo pasa; por encima se rechaza aunque haya cuota; otro plan con otro máximo; usuario sin `maxFileBytes` se rechaza.
**Verificación:** `storage.test.mjs` y `quota-e2e.test.mjs` (ACC-010). `npm test` completo en verde.

### Tarea 6: despliegue al proyecto real (S) — K-285
Acción aparte que pido antes de hacerla. Orden (ADR 0017):
1. `firebase deploy --only functions` (alta con catálogo y `onPlansUpdated`).
2. `firebase deploy --only remoteconfig` (dispara `onPlansUpdated`: completa `maxFileBytes` y ajusta `quotaBytes` de los usuarios existentes).
3. Comprobar en Firestore que los usuarios tienen `maxFileBytes`.
4. `firebase deploy --only storage`.
5. Cambiar un valor desde la consola de Remote Config y ver que llega a `users/{uid}`; volver a dejarlo como en el repo.

**Resultado (2026-10-14):** funciones desplegadas (`onPlansUpdated` nueva). Antes de publicar, la única cuenta (`ABfco0IM…`, 100 MiB a mano y 42 MiB usados) pasó a `plan: "pro"` por decisión del usuario, para que no quedara pasada de cuota. Al volver a publicar el template, `onPlansUpdated` le escribió `quotaBytes` 50 GiB y `maxFileBytes` 100 MiB a los 7 s, sin tocar `usedBytes`. Reglas de Storage desplegadas. El paso 5 queda cubierto por esa publicación. Las reglas de Firestore no se desplegaron en este plan.

### Tarea 7: Android, ids y máximo por archivo del servidor (S) — K-286
Decisión del usuario (2026-10-14): las apps se dividen en dos partes. Esta primera no agrega dependencias.
- [x] `StorageQuota.maxFileBytes` (opcional): lo lee `FirestoreQuotaSource` de `users/{uid}` y lo guarda `QuotaCacheImpl`.
- [x] `PlanCatalog`: ids del backend `free`, `plus` y `pro` (antes `reader` y `library`); `maxFileBytes(quota)` usa el del servidor si lo conoce y, si no, el valor fijo del plan (ACC-008: los dos coinciden con el catálogo).
- [x] `quotaBlock` (PLN-014) y la subida usan ese máximo.
**Verificación:** tests JVM (`./gradlew :app:testDebugUnitTest`) con ACC-008 y ACC-010; el de la caché, en emulador si corresponde.

### Tarea 8: iOS, lo mismo que la Tarea 7 (S) — K-287
**Verificación:** `swift test` en `ReaderDomain` y tests de la app en el simulador.

## Fuera de este plan

- Apps, segunda parte: precios y espacio del catálogo desde Remote Config, con valores por defecto propios (ACC-007, ACC-008), reusando la configuración de Remote Config que agrega la sincronización de notas (ADR 0016, PR #38). K-290, después de que se integre ese PR.
- Cobro y cambio de plan desde la app.
