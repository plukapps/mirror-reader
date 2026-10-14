# ADR 0017 — Catálogo de planes en Remote Config, aplicado por el backend

**Estado:** provisional. Depende de los ADR 0007 y 0008.

## Contexto

ACC-007 a ACC-010 piden un catálogo de planes (`free`, `plus`, `pro`) con espacio total, tamaño máximo por archivo y precio mensual y anual, que se pueda cambiar sin publicar la app. Los límites que valen son los del servidor (ACC-008).

Hoy los límites están fijos en dos lugares:

- `functions/src/accounts.ts`: `FREE_PLAN.quotaBytes` = 15 MiB, que se copia a `users/{uid}.quotaBytes` al registrarse.
- `storage.rules`: tope de 100 MB por archivo escrito en la regla.

Lo que verificamos en la documentación oficial (2026-10-14):

- Las reglas de Storage pueden leer Firestore (`firestore.get()`), pero **no** Remote Config.
- El Admin SDK lee el template publicado (`getRemoteConfig().getTemplate()`).
- Cloud Functions tiene un trigger `onConfigUpdated` (`firebase-functions/v2/remoteConfig`) que se dispara con cada versión publicada del template, también en un rollback.
- `firebase.json` acepta `"remoteconfig": { "template": "..." }`, así que el template se versiona en el repo y se publica con `firebase deploy --only remoteconfig`.
- Remote Config no tiene emulador: los tests no pueden tocarlo.

## Opciones

1. **Solo informativo.** La app lee Remote Config para mostrar los planes y el backend sigue con valores fijos. Hay que mantener dos fuentes iguales a mano. Contradice ACC-008.
2. **Remote Config como fuente, aplicado por el backend.** Las funciones leen el template y copian los límites del plan a `users/{uid}`. Las reglas de Storage leen ese documento, como ya hacen con la cuota.
3. **Catálogo en Firestore** (`plans/{id}`), legible por la app y por las reglas. Sin trigger extra, pero cada subida cuesta otra lectura en la regla y se pierde la consola y el historial de versiones de Remote Config.

## Decisión

Opción 2.

- Un solo parámetro JSON `plans` en el template, con este formato:

  ```json
  {
    "free": { "storageGb": 0.0146484375, "maxFileMb": 2,   "priceMonthly": 0,    "priceYearly": 0,     "currency": "USD" },
    "plus": { "storageGb": 5,            "maxFileMb": 50,  "priceMonthly": 2.99, "priceYearly": 24.99, "currency": "USD" },
    "pro":  { "storageGb": 50,           "maxFileMb": 100, "priceMonthly": 5.99, "priceYearly": 49.99, "currency": "USD" }
  }
  ```

  Los valores iniciales son los de PLN-001 (`specs/product/12-plans.md`). Los ids son `free`, `plus` y `pro` (Gratis, Lector y Biblioteca); el espacio gratuito, 15 MiB, se escribe 15 / 1024 GB. Decisión del usuario (2026-10-14).

  Un solo parámetro se publica entero: no hay estados intermedios con un plan a medio cambiar, y agregar un plan no agrega claves (ACC-005).
- Unidades binarias: 1 GB = 1024³ bytes, 1 MB = 1024² bytes (igual que los 100 MB de hoy en la regla). El servidor redondea hacia abajo a bytes enteros.
- Solo cuenta el **valor por defecto** del parámetro. Las condiciones de Remote Config no se usan para `plans`: si se usaran, la app podría ver un valor distinto del que aplica el servidor.
- `users/{uid}` suma el campo `maxFileBytes`. `quotaBytes` y `maxFileBytes` los escribe solo el servidor.
- `onUserCreated` lee el template y crea el documento con los límites de `free`.
- Una función nueva, `onPlansUpdated` (`onConfigUpdated`), vuelve a leer el template y actualiza `quotaBytes` y `maxFileBytes` de los usuarios de cada plan cuyos valores cambiaron (ACC-009). Escribe en lotes y solo los documentos que difieren, así que repetir el evento no cambia nada.
- La regla de Storage reemplaza los 100 MB fijos por `maxFileBytes` del mismo documento que ya lee para la cuota: no suma lecturas.
- **Validación:** si `plans` falta, no es JSON o algún plan tiene un valor inválido (no numérico, negativo, falta `free`), el template entero se ignora: `onPlansUpdated` no toca a nadie y `onUserCreated` usa los valores por defecto del código (iguales al template del repo). Se registra un error en los logs.
- **App (ACC-008):** usa `quotaBytes` y `maxFileBytes` de `users/{uid}`, como ya hace con la cuota. Para mostrar el catálogo y los precios lee `plans` con el SDK de Remote Config, con valores por defecto en la app iguales al template del repo. Si no conoce todavía `maxFileBytes`, no rechaza localmente: el servidor decide.
- El precio es informativo. Cuando haya cobro, el precio real y localizado lo ponen Google Play y App Store; ese cambio necesitará su propio ADR.

## Consecuencias

- **Un solo template para todo el proyecto.** `firebase deploy --only remoteconfig` reemplaza el template entero. Todos los parámetros de Remote Config, también `notes_sync_enabled` del ADR 0016 con sus condiciones, tienen que estar en `remoteconfig.template.json`; si no, el próximo deploy los borra. La rama que llegue segunda los junta en ese archivo.

- El template vive en `code/backend/v1/remoteconfig.template.json` y se publica con el CLI. Un cambio desde la consola funciona igual (dispara `onPlansUpdated`), pero deja el repo desactualizado: hay que traerlo de vuelta.
- Orden de despliegue la primera vez: funciones, después el template (el trigger completa `maxFileBytes` en los usuarios existentes) y por último las reglas. Si las reglas salieran antes, un usuario sin `maxFileBytes` no podría subir.
- Bajar un límite nunca borra nada: solo bloquea nuevas subidas (LIB-009). Con el catálogo inicial el máximo por archivo del plan gratuito baja de 100 MB a 2 MB: los libros ya subidos se quedan, pero uno de más de 2 MB no se puede volver a subir (por ejemplo desde otro dispositivo que lo tenga solo en local).
- `onPlansUpdated` recorre a todos los usuarios de un plan cuando cambia. Con pocos usuarios está bien. Con muchos, habrá que paginar o encolar la actualización.
- Los cambios del template tardan en llegar a la app (caché del SDK de Remote Config). Los límites aplicados no dependen de eso, porque vienen de `users/{uid}`.
- Los tests prueban la lectura y validación del catálogo y la actualización de usuarios con el catálogo inyectado, sin tocar Remote Config. En el emulador de Functions (`FUNCTIONS_EMULATOR`), `onUserCreated` usa los valores por defecto del código en lugar de leer el template.
