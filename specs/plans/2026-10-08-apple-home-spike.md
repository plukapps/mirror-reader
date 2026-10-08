# Apple (Mac, después iOS) — Prueba de la pantalla de inicio

**Objetivo:** una prueba de desarrollo (spike) que abre la app en la Mac y muestra Inicio con datos falsos. Sirve para aprender el flujo de Apple (proyecto, compilar, correr, test) y validar la estructura antes de la primera rebanada real.

**Spec que toma de referencia:** `specs/product/07-home.md` (HOM-001, HOM-002, HOM-008 a HOM-010), solo la parte visual. Diseño: "02 — Home" de `design/Margin Ebook App.dc.html` (no hay diseño de Mac; se adapta a una ventana).

## Fuera de alcance

Importar libros, biblioteca real, persistencia, Readium, Firebase, navegación a otros destinos, iOS (la estructura lo prevé, pero no se compila para iOS todavía), firma para distribución.

## Decisiones (provisorias, se formalizan en un ADR al cerrar la prueba)

- Carpeta `code/apple/`: un solo proyecto para Mac e iOS.
- SwiftUI, Swift, MVVM con `@Observable`. Capas como Android: `UI`, `Domain`, `Data`.
- El proyecto se genera con XcodeGen desde `code/apple/project.yml`. El `.xcodeproj` generado no va al repo.
- `Domain` es un paquete Swift local (`code/apple/Packages/ReaderDomain`), testeable con `swift test` sin Xcode.
- Bundle id `com.pluk.reader` y destino macOS 14 (Sonoma). Firma local ("Sign to Run Locally").
- Requisito de máquina: Xcode 16.2 (el último que corre en macOS 14.6).

## Estructura destino

```
code/apple/
  project.yml                 XcodeGen: target app macOS (iOS después)
  README.md                   cómo generar, compilar y correr
  App/                        PlukReaderApp.swift, Info, assets, fuentes
  App/UI/Home/                HomeView, HomeViewModel
  App/Data/                   FakeLibraryRepository (datos de prueba)
  Packages/ReaderDomain/      LibraryBook, homeContent(books), greetingFor(hour) + tests
```

## Tareas

1. **K-074 Esqueleto:** `project.yml`, app vacía que abre una ventana; `.gitignore` del `.xcodeproj` y de `DerivedData`; README. Verificación: `xcodegen` + `xcodebuild build` y la ventana se abre con ⌘R.
2. **K-075 Dominio:** paquete `ReaderDomain` con el modelo y las reglas de Inicio (las mismas de Android: continuar leyendo, leyendo, agregados, terminados, saludo), con tests que citan los IDs HOM. Verificación: `swift test`.
3. **K-076 Pantalla:** `HomeView` + `HomeViewModel` con un repositorio falso (portadas de color, 6 a 8 libros), fuentes de ADR 0009. Verificación: se ve a mano en la Mac; un test del ViewModel.
4. **K-077 Cierre:** `specs/platforms/apple.md` (borrador), ADR 0010 (stack de Apple), AGENTS.md (sección Apple), Kanban.

## Riesgos

- Xcode 16.2 viejo para publicar: la App Store exige SDK reciente; antes de publicar hay que actualizar macOS y Xcode.
- Sin experiencia previa del usuario en Apple: cada tarea termina con instrucciones de cómo verlo en Xcode.
