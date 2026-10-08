# Android — Pantalla de inicio

**Objetivo:** que la app arranque en Inicio, según la pantalla "02 — Home" de `design/Margin Ebook App.dc.html`, con "Continuar leyendo", "Para ti" y la barra inferior de cuatro destinos.

**Spec que implementa:** `specs/product/07-home.md` (HOM-001 a HOM-007), aprobado. Plataforma: AND-001, AND-002.

## Sin cambios de spec ni ADR

No hay decisión técnica nueva: se reutilizan Room, Hilt y Navigation Compose (ADR 0005). Ya existe `ReadingPositionEntity.updatedAt`, así que no hay migración.

## Estructura destino

```
domain/model/       LibraryBook + lastReadAt (Long?); HomeContent (continueReading, forYou)
domain/             homeContent(books): función pura (HOM-002, 003, 004)
                    greetingFor(hour): Greeting (HOM-001)
data/local/db/      BookProgress + updatedAt; consulta observeProgress con updatedAt
ui/home/            HomeScreen, HomeViewModel (StateFlow<HomeUiState>)
ui/navigation/      Routes.HOME (start), MainDestination, MarginBottomBar, scaffold compartido
```

`ui/library/BookCover` se reutiliza para las portadas. El texto del saludo vive en `strings.xml`.

## Reglas de negocio

- **Continuar leyendo:** libros con estado `Reading`, el de mayor `lastReadAt`. Empate: el importado más reciente.
- **Para ti:** libros con estado `New`, orden de la biblioteca (más reciente primero). Sección oculta si no hay.
- **Saludo:** 5–11 h mañana, 12–19 h tarde, resto noche.

## Tareas

### Tarea 1: modelo y reglas puras (S) — K-035
- [ ] `lastReadAt` en `BookProgress` y `LibraryBook`; `LibraryRepositoryImpl` lo propaga.
- [ ] `homeContent` y `greetingFor` en `domain`, sin Android.
**Verificación:** tests JVM `HomeContentTest` (HOM-002, 003, 004 y los escenarios de "hoy gana a ayer", "terminado sale de Continuar") y `GreetingTest` (HOM-001). `./gradlew :app:testDebugUnitTest`.

### Tarea 2: barra inferior y navegación (M) — K-036
- [ ] `Routes.HOME` pasa a ser `startDestination`. Barra con Inicio, Buscar, Estantes, Perfil; "Estantes" abre la biblioteca; visible en Inicio y Biblioteca, oculta en el lector (HOM-005).
- [ ] Buscar y Perfil: `// TODO` y aviso breve ("Llega más adelante") sin navegar (HOM-006).
- [ ] Etiquetas de accesibilidad y estado seleccionado (HOM-007).
**Verificación:** test Compose de la barra en emulador (HOM-005, 006); revisión visual contra el diseño.

### Tarea 3: pantalla de Inicio (L) — K-037
- [ ] `HomeViewModel` con `HomeUiState` (saludo, continuar, para ti, biblioteca vacía).
- [ ] `HomeScreen`: saludo, tarjeta "Continuar leyendo" con portada, progreso y %, fila "Para ti" con "Ver todo" a la biblioteca. Estado sin lectura (HOM-003): invitación a ir a la biblioteca; con biblioteca vacía, botón Importar con el mismo selector y `ImportBooksUseCase` de la biblioteca (LIB-001).
- [ ] Textos en `strings.xml`; teléfono y tablet con ancho máximo de contenido (HOM-007).
**Verificación:** tests de `HomeViewModel` en JVM con repositorio falso; test Compose en emulador; preview.

### Tarea 4: cierre (S) — K-038
- [ ] Quitar de `AGENTS.md`/docs lo que diga que la biblioteca es el inicio; actualizar la nota de `specs/plans/2026-10-06-android-library.md` si hace falta.
- [ ] Verificación a mano en el teléfono con tus EPUB: continuar, volver del lector y ver el progreso actualizado, libro terminado, biblioteca vacía.
**Verificación:** `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

## Checkpoints

- Tras la Tarea 1: tests JVM verdes.
- Tras la Tarea 3: la app arranca en Inicio y se puede leer de punta a punta.

## Riesgos

- Los tests Compose y de Room corren en emulador y no hay emulador; según tus notas no se corren en el teléfono sin preguntarte. Te aviso antes de ese paso.
- Volver del lector debe refrescar Inicio: se resuelve porque el repositorio expone un `Flow` de Room.
- `LibraryBook` gana un campo: hay que actualizar los tests y fakes que lo construyen.

## Tarjetas del Kanban

K-035 a K-038, una por tarea. K-034 (spec) pasa a Hecho.
