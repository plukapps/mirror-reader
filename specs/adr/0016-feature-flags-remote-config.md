# ADR 0016 — Funciones que se prenden y apagan con Remote Config

**Estado:** provisional. Depende del ADR 0007 (Firebase).

## Contexto

La sincronización de notas (SYN-016 y SYN-017, rebanada de notas) tiene que poder prenderse o apagarse sin publicar una versión nueva de la app, para todas las cuentas o según el plan (ACC-011). Van a llegar planes pagos (Gratis, Lector, Biblioteca, `specs/product/12-plans.md`) y otras funciones con el mismo problema. Pedido del usuario (2026-10-14): usar Firebase Remote Config.

## Decisiones

### Remote Config, un parámetro booleano por función

- Primer parámetro: `notes_sync_enabled`. Los nombres siguen `<función>_enabled`. También sirve para elegir entre dos opciones de una pantalla: `guest_banner_no_backup_enabled` (GST-025, 2026-10-16) elige el texto del banner sin cuenta; para una prueba A/B se usa una condición por porcentaje de usuarios. Y para números que se afinan sin publicar, un parámetro `NUMBER` por valor (`Setting` en Android): el primero es `guest_notice_snooze_days` (GST-026, por defecto 7); `FeatureTemplateTest` exige que esté en el template con el mismo valor.
- El valor por defecto dentro de la app es `true` (SYN-017: si no se sabe, vale prendida; hoy todas las cuentas tienen todo, ACC-011). El valor por defecto en la consola también es `true`.
- **Por plan:** la app manda la señal personalizada `plan` (`free`, o el que tenga `users/{uid}.plan`) con `setCustomSignals`. Una condición de la consola, por ejemplo `app.customSignal['plan'] == 'free'`, cambia el valor para ese plan. Requiere firebase-config 22.1.0 o posterior en Android (el BoM 35 trae la 24.0.0) y 11.8.0 o posterior en iOS (el proyecto usa la 12.14.0). Fuente: documentación de Remote Config, "Custom signal conditions".
- **Para una cuenta o una instalación** (pruebas): una condición por `app.firebaseInstallationId`.

### El template se versiona en el repo

- El template entero vive en `code/backend/v1/remoteconfig.template.json` (`firebase.json` → `remoteconfig.template`) y se publica con `firebase deploy --only remoteconfig`. También lo usa el catálogo de planes (`plans`, ADR 0017, otra rama).
- Publicar reemplaza el template del proyecto entero: un parámetro o una condición que no esté en el archivo desaparece. Por eso todo cambio pasa por el archivo, también las condiciones de prueba por instalación, y un cambio hecho en la consola se trae al repo antes del próximo deploy.
- Dos tests lo protegen: `tests/remoteconfig.test.mjs` (backend) y `FeatureTemplateTest` (Android, cada `Feature` tiene su parámetro con el mismo valor por defecto).
- Motivo: el 2026-10-14 un deploy del catálogo desde su rama, sin `notes_sync_enabled`, borró el parámetro publicado minutos antes. Esta rama base (`feature/remote-config`) junta en un solo archivo lo que necesitan las dos.

### Cuándo se lee

- Al arrancar se activa lo último que se bajó (`activate`) y se pide lo nuevo (`fetchAndActivate`), con el intervalo mínimo por defecto (12 h).
- Con la app a la vista se escuchan los cambios en tiempo real (`addOnConfigUpdateListener`), así apagar o prender llega en segundos sin esperar ese intervalo.
- Al cambiar la sesión o el plan se vuelve a mandar la señal `plan` y se pide de nuevo.

### Detrás de una interfaz de dominio

`FeatureFlags` (dominio) expone cada función como un `StateFlow<Boolean>`. La implementación con Remote Config vive en `data`. Los tests usan un doble y nunca tocan Firebase.

### El servidor no lo hace cumplir (por ahora)

Las reglas de Firestore no pueden leer Remote Config. Con el flag apagado, una app modificada todavía podría escribir notas en su propia cuenta. Se acepta porque hoy no se cobra y el usuario solo toca sus propios datos (ACC-004). Cuando una función dependa de un plan pago, la rebanada de cobro decide cómo hacerlo cumplir en el servidor, por ejemplo con un mapa `features` en `users/{uid}` escrito solo por el servidor, o con custom claims.

## Alternativas descartadas

- **Mapa `features` en `users/{uid}`:** el servidor lo podría hacer cumplir, pero exige una función y una migración de las cuentas existentes, y no sirve como interruptor general. Puede sumarse más adelante (ver arriba).
- **Documento `config/features` en Firestore:** un interruptor general sin condiciones por plan.

## Consecuencias

- Una dependencia nueva: `firebase-config` (Android, por el BoM) y `FirebaseRemoteConfig` (iOS, cuando la app de iOS tenga notas).
- Crear o cambiar un parámetro es una acción sobre el proyecto real y se pide al usuario.
- Las ramas que tocan Remote Config parten de `feature/remote-config` o la integran, y agregan sus parámetros al mismo template. El catálogo de planes usa ACC-007 a ACC-010; por eso este requisito es ACC-011.
