# Mi plan en Plus y Pro

**Objetivo:** que "Mi plan" (P04) muestre el plan real de la cuenta. Hoy siempre muestra la variante Free, también con una cuenta `plus` o `pro` (K-292).

**Specs que implementa:** `specs/product/12-plans.md` (PLN-017; cambia PLN-005 y PLN-012). Usa ACC-008 y ACC-010 (máximo por archivo del servidor).
**Rama:** `feature/my-plan-paid`, encima de `claude/firebase-remote-config-plans-6ed068` (PR #39). Cuando el #39 se integre, el PR pasa a apuntar a `develop`.

## Decisiones (usuario, 2026-10-14)

- Solo lo que existe hoy: nombre del plan con "Tu plan", espacio usado con barra, dispositivos, máximo por archivo de la cuenta, la oferta de Pro solo en Plus, "Cambiar de plan" y "Restaurar compras".
- Sin precio pagado, renovación, "Administrar suscripción" ni Familia (llegan con el cobro).
- Free también muestra el máximo por archivo de la cuenta en lugar de los 2 MB fijos.
- En Planes (P01), "Tu plan" va en la tarjeta del plan de la cuenta, no solo en Free (cambia PLN-005). En Plus reemplaza a "Más elegido".
- Ids `free`, `plus` y `pro` (K-293). Ningún número escrito a mano: el máximo de la cuenta sale del servidor (`PlanCatalog.maxFileBytes`) y los del plan que se ofrece, de `DefaultPlans` (K-294).

## Tareas

### Tarea 1: spec y plan (S) — K-296
- [x] PLN-017 y cambio de PLN-012, escenarios, este plan.
**Verificación:** revisión del usuario.

### Tarea 2: Android (S) — K-297
- [x] `MyPlanUiState` con el plan de la cuenta (`PlanCatalog.planOf`) y su máximo por archivo (`PlanCatalog.maxFileBytes`).
- [x] `MyPlanScreen`: nombre del plan, "Archivos de hasta {máximo}", la tarjeta de Plus solo en Free, la de Pro solo en Plus, ninguna en Pro. Textos en `strings.xml`.
- [x] `PlansUiState`: el plan de la cuenta en lugar de `isOnFreePlan`; "Tu plan" en esa tarjeta (PLN-005).
**Verificación:** tests JVM de los `ViewModel` (PLN-005, PLN-012, PLN-017); test de Compose escrito y compilado (no se corre en el dispositivo, ver `AGENTS.md`).

### Tarea 3: iOS (S) — K-298
- [x] Lo mismo en `MyPlanView` (modelo y vista) y "Tu plan" en Planes, textos en `PlanTexts.swift`.
**Verificación:** tests de la app en el simulador del iPhone.

### Tarea 4: verificación a mano y PR (S) — K-299
- [x] Instalar en el teléfono y el iPhone; con la cuenta `pro`, Mi plan dice Pro, 100 MB y sin oferta.
- [x] PR contra `claude/firebase-remote-config-plans-6ed068`.

**Resultado (2026-10-14):** el usuario vio en el dispositivo Mi plan con "Pro" en su cuenta `pro`.

## Fuera de este plan

- Precio pagado, renovación, administrar suscripción, Familia, plan vencido (P06).
