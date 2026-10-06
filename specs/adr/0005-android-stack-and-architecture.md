# ADR 0005 — Stack y arquitectura de la app Android

**Estado:** aceptado. Concreta ADR 0001 (clientes nativos) para Android.

## Contexto

La app Android nace con una actividad por pantalla, estado en `SharedPreferences` y un `ViewModel` sin inyección de dependencias. Eso no escala a biblioteca, anotaciones y sincronización. El dueño del producto fijó el stack y la arquitectura que quiere en todas sus apps Android.

## Decisión

**Lenguaje y UI**
- Kotlin nativo.
- Interfaz en Jetpack Compose con Material 3.
- Navigation Compose, con **una sola actividad** (`MainActivity`) y un `NavHost`.

**Arquitectura: MVVM en capas**

| Capa | Paquete | Responsabilidad | Puede depender de |
|------|---------|-----------------|-------------------|
| ui | `ui/` | Pantallas Compose, `ViewModel`, estado de UI, navegación, tema | domain |
| domain | `domain/` | Modelos propios, interfaces de repositorio, casos de uso con lógica real | nada de Android ni de Compose |
| data | `data/` | Implementaciones de repositorio, Room, DataStore, red, motor de EPUB | domain |
| di | `di/` | Módulos de Hilt que conectan las capas | todas |

- Un `ViewModel` expone un único `StateFlow<UiState>`. Las pantallas no tocan `data` directamente.
- Los casos de uso se crean solo cuando hay lógica. No se crean casos de uso que solo reenvían una llamada.
- Readium es el motor de EPUB (ADR 0004). Sus tipos (`Publication`, `Locator`) se aceptan en `domain` solo dentro del modelo `OpenedBook`, porque cada plataforma es nativa y el dominio no se comparte. Todo lo demás en `domain` son modelos propios.

**Inyección y asincronía**
- Hilt para inyección de dependencias, con **KSP** (kapt no es compatible con el Kotlin integrado de AGP 9).
- Coroutines y Flow para todo lo asíncrono.

**Persistencia local**
- Room: datos estructurados (posición de lectura hoy; biblioteca, anotaciones y cola de sincronización después).
- DataStore Preferences: ajustes del usuario (tema, tamaño de letra, modo de lectura).
- No se usa `SharedPreferences`.

**Backend remoto**
- Retrofit con OkHttp y Gson. Las dependencias se agregan en la rebanada de sincronización, no antes.
- El backend se consume como API REST detrás de interfaces de repositorio de `domain`, así que la elección de backend (ADR 0003) no se filtra a `ui`.

**Pantalla del lector**
- `ReaderScreen` (Compose) reemplaza a `ReaderActivity`.
- El navegador de Readium es un `Fragment`. Se aloja en Compose con `AndroidFragment`, dentro de `MainActivity`, que por eso es una `FragmentActivity`.
- La fábrica de fragmentos del navegador se entrega a `MainActivity` mediante un proveedor inyectado (`@Singleton`), porque Android debe poder instanciar el fragmento al restaurar la actividad.
- Si Android restaura la actividad tras perder el proceso, no hay libro abierto y el proveedor está vacío: se descarta el estado guardado y se vuelve a la pantalla de inicio. La posición ya está en Room.

## Alternativas consideradas

- **Mantener actividades por pantalla y `SharedPreferences`.** Descartado: no escala y contradice el stack definido.
- **Un solo módulo Gradle con capas por paquete (elegido) frente a un módulo por capa.** Un módulo por capa agrega configuración que hoy no se justifica. Se puede separar más adelante sin cambiar las dependencias entre capas.
- **Gson frente a kotlinx.serialization.** Se usa Gson porque es parte del stack fijado. La plantilla trajo el plugin de kotlinx.serialization y se elimina si nada lo usa.

## Consecuencias

- Hay que mantener el código en las cuatro capas y respetar quién depende de quién.
- Las pruebas de la lógica de presentación corren en JVM con repositorios falsos. Las pruebas que tocan Room, DataStore o Readium corren en emulador con Hilt de pruebas.
- Retrofit, OkHttp y Gson no están en el proyecto hasta K-010 (sincronización). Eso evita dependencias sin uso.
- El tema "plantilla" `ReaderTheme` pasa a `ui/theme`.
