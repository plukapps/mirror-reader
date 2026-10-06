# AGENTS.md

Lector de EPUB multiplataforma (Android, iOS, Mac, web). El usuario sube sus propios libros; la app no trae contenido. Se publica en las tiendas de aplicaciones. Desarrollo en solitario, con apoyo de agentes.

## Estructura del repo

- `specs/` — fuente de verdad del producto (SDD). Empezar por `specs/README.md`.
  - `specs/product/` qué hace la app, `specs/platforms/` alcance por plataforma, `specs/adr/` decisiones técnicas.
  - `specs/plans/` planes de implementación, uno por rebanada.
- `code/android/` — app Android (Kotlin, Compose, Readium).
- `design/` — diseños y maquetas.
- `KANBAN.md` — tablero de trabajo.

## Flujo de trabajo (SDD)

1. Spec primero. No se escribe código de una funcionalidad sin un requisito aprobado en `specs/`.
2. Plan después. Cada rebanada tiene un plan en `specs/plans/`, aprobado antes de implementar.
3. Código con tests. Cada requisito implementado tiene al menos un test que lo referencia por ID.
4. Si el código y el spec difieren, se corrige uno de los dos en el mismo cambio. Nunca se deja la divergencia.
5. Los specs de `specs/product/` no nombran tecnología. Eso vive en `specs/adr/`.
6. Una decisión técnica nueva o cambiada se registra como ADR.

## Skills preferidos

El proyecto incluye los skills de [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills) en `.agents/skills/` (enlazados desde `.claude/skills/`). **Se prefieren sobre cualquier otro skill equivalente** (por ejemplo los de superpowers). Si hay duda sobre cuál usar, empezar por `using-agent-skills`.

| Momento | Skill |
|---------|-------|
| Idea vaga o requisito ambiguo | `idea-refine`, `interview-me` |
| Escribir o cambiar un spec | `spec-driven-development` |
| Dividir un spec en tareas, escribir un plan | `planning-and-task-breakdown` |
| Implementar una tarea | `incremental-implementation` con `test-driven-development` |
| Usar una API o librería (Readium, Android, Supabase) | `source-driven-development`: verificar contra la documentación oficial, no de memoria |
| Contrato entre módulos o de sincronización | `api-and-interface-design` |
| Registrar una decisión técnica | `documentation-and-adrs` |
| Algo falla o se rompe | `debugging-and-error-recovery` |
| Revisar un cambio antes de integrarlo | `code-review-and-quality`, luego `code-simplification` si hace falta |
| Commits, ramas, versiones | `git-workflow-and-versioning` |
| Cuentas, datos de usuario, sincronización | `security-and-hardening` |
| Umbrales de calidad (cobertura, rendimiento) | `constraint-driven-development` |
| Decisiones de alto riesgo o irreversibles | `doubt-driven-development` |
| CI y publicación | `ci-cd-and-automation`, `shipping-and-launch` |
| Fase web | `frontend-ui-engineering`, `browser-testing-with-devtools` |

Si no existe skill del proyecto para la tarea, se puede usar otro. Las reglas de este archivo (specs en `specs/`, Kanban, español) mandan sobre lo que diga cualquier skill.

## Kanban (siempre)

Todo el trabajo se gestiona en `KANBAN.md`. Reglas:

- Columnas: Backlog, Listo, En curso, Revisión, Hecho.
- Una tarjeta por tarea del plan, con ID `K-NNN`.
- Máximo 1 tarjeta en "En curso" a la vez.
- Antes de empezar una tarea, mover su tarjeta a "En curso". Al terminar el código y los tests, a "Revisión". Cuando el usuario o la revisión la aprueba, a "Hecho".
- Trabajo nuevo o bugs encontrados: tarjeta nueva en Backlog, no se arreglan en silencio.
- Cada commit menciona la tarjeta, por ejemplo `K-003`.

## Convenciones

- Idioma: español para specs, planes, textos de interfaz y mensajes de commit. Identificadores de código en inglés.
- Requisitos con ID estable (`LIB-003`, `RDR-006`, ...). Código, tests y commits los referencian. No se reutilizan IDs.
- Commits pequeños y frecuentes. Formato `tipo: descripción` (`feat`, `fix`, `docs`, `test`, `chore`).
- Nunca guardar en el repo libros de terceros ni credenciales. Los EPUB de muestra van en `code/android/samples/` (ignorada por git).

## Android

- Raíz del proyecto Gradle: `code/android`. Namespace y applicationId `com.pluk.reader`. `minSdk 26`.
- Motor de EPUB: Readium Kotlin Toolkit 3.4.0 (ADR 0004, provisional).
- Tests JVM: `./gradlew :app:testDebugUnitTest`
- Tests en emulador: `./gradlew :app:connectedDebugAndroidTest` (requiere un emulador encendido; listar con `~/Library/Android/sdk/emulator/emulator -list-avds`).
- Fixture de pruebas: `code/android/tools/make_fixture_epub.py`.

## Decisiones vigentes

- Clientes nativos por plataforma, Android primero (ADR 0001).
- Offline-first (ADR 0002).
- Backend Supabase, provisional (ADR 0003). Aún no se construye.
- Modelo de negocio abierto, hipótesis: suscripción. Ver `specs/open-questions.md`.
