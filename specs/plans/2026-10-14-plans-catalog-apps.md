# Catálogo de planes en las apps

**Objetivo:** que Android e iOS tomen los números de cada plan (espacio, máximo por archivo, precio mensual y anual) del catálogo de Remote Config (`plans`, ADR 0017), en lugar de los valores fijos de `DefaultPlans`, que quedan como valores por defecto (K-290).

**Specs que implementa:** PLN-001 (cambiado), ACC-007, ACC-008.
**Decisiones técnicas:** ADR 0016 (Remote Config compartido) y ADR 0017 (catálogo).
**Rama:** `feature/plans-catalog-apps`, encima de `feature/my-plan-paid` (PR #40), que a su vez está encima del #39.

## Decisiones

- Una sola fuente de Remote Config por app: `RemoteFlagSource` (ADR 0016) se amplía para leer textos y carga juntos los valores por defecto de las funciones y el de `plans` (el JSON de `DefaultPlans`). Si se cargaran por separado, el último pisaría al primero.
- El catálogo se lee como en el backend (`plans.ts`): ids `free`, `plus` y `pro`; `storageGb`, `maxFileMb`, `priceMonthly`, `priceYearly` y `currency`; un plan inválido o que falte `free` invalida el catálogo entero y quedan los de `DefaultPlans`. Un test por app usa el JSON del template del repo.
- `RemoteFeatureFlags` ya hace el ciclo (señal `plan`, `activate`, `fetchAndActivate`, escucha en tiempo real): al publicar, también publica el catálogo.
- Precios: número y moneda en el modelo (`priceMonthly`, `priceYearly`, `currency`); se muestran fijos como "$2.99" (decisión del usuario, 2026-10-14); la equivalencia por mes del anual se calcula (anual / 12, redondeado al centavo).
- El máximo por archivo de la cuenta sigue saliendo de `users/{uid}.maxFileBytes` (ACC-008, ACC-010); el catálogo da el de cada plan para mostrarlo y como respaldo.
- Gratis: su espacio sigue siendo la cuota de la cuenta si la cuenta está en Free (PLN-001, K-291); si no, el del catálogo.

## Tareas

### Tarea 1: spec y plan (S) — K-301
- [x] PLN-001, ADR 0017, este plan.
**Verificación:** revisión del usuario.

### Tarea 2: Android, dominio (M) — K-302
- [ ] `Plan` con precios numéricos y moneda; texto "$2.99" y equivalencia por mes calculados.
- [ ] `parsePlans(json)` con la validación del backend; test con el JSON del template.
- [ ] `RemotePlans`: catálogo vigente (`StateFlow`), con `DefaultPlans` por defecto; `PlanCatalog` lo usa.
**Verificación:** tests JVM (ACC-007, ACC-008, PLN-001), verificados rompiendo el código.

### Tarea 3: Android, Remote Config y pantallas (M) — K-303
- [ ] `RemoteFlagSource` con lectura de textos; `FirebaseRemoteFlagSource` carga los valores por defecto de funciones y catálogo juntos.
- [ ] `RemoteFeatureFlags` publica también el catálogo.
- [ ] Planes, Mi plan, hojas, Perfil, lector y subida usan el catálogo vigente y se actualizan cuando cambia.
**Verificación:** tests JVM de los `ViewModel` y de `RemoteFeatureFlags` con una fuente falsa; los de Compose, compilados.

### Tarea 4: iOS, dominio (M) — K-304
- [ ] Lo mismo que la Tarea 2 en `ReaderDomain`.
**Verificación:** `swift test`.

### Tarea 5: iOS, Remote Config y pantallas (M) — K-305
- [ ] Lo mismo que la Tarea 3.
**Verificación:** tests de la app en el simulador.

### Tarea 6: verificación a mano y PR (S) — K-306
- [ ] Instalar en el teléfono y el iPhone y ver los precios y espacios de hoy (los del catálogo publicado coinciden con los por defecto).
- [ ] Opcional, con permiso del usuario: cambiar un precio en Remote Config, verlo llegar a las dos apps y volver a dejarlo como en el repo.
- [ ] PR contra `feature/my-plan-paid`.

## Fuera de este plan

- Precio con el formato del país del dispositivo y precio de la tienda (llega con el cobro).
- Textos de los planes desde el servidor.
