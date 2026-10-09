# ADR 0010 — Stack y arquitectura de las apps de Apple (Mac e iOS)

**Estado:** provisional. Concreta ADR 0001 (clientes nativos) para Mac e iOS. Sale de la prueba de Inicio (plan `specs/plans/2026-10-08-apple-home-spike.md`, K-074 a K-077) y se revisa en la primera rebanada real.

## Contexto

ADR 0001 fija Swift para Mac e iOS y anticipa que pueden compartir código. Android ya tiene stack y capas (ADR 0005). Hace falta lo mismo para Apple antes de crecer, con dos condiciones: un solo desarrollador sin experiencia previa en Apple, y trabajo con agentes, que necesitan editar el proyecto como texto.

## Decisión

**Un solo proyecto para Mac e iOS**
- Carpeta `code/apple/`, junto a `code/android/` y `code/backend/`.
- Swift y SwiftUI. Mac primero; iOS se suma como destino del mismo target, no como otro proyecto. Desde K-097 el target `Reader` tiene los dos destinos (`supportedDestinations: [macOS, iOS]`) y los ajustes solo de Mac van condicionados con `[sdk=macosx*]`.
- Versiones mínimas: macOS 14 (Sonoma) y, cuando se sume, iOS 17. Las dos traen `@Observable`.

**Proyecto generado con XcodeGen**
- El proyecto se describe en `code/apple/project.yml` y `xcodegen` genera `Reader.xcodeproj`, que no va al repo.
- Motivo: el `.pbxproj` es ilegible, choca en cada merge y es frágil de editar a mano o con agentes. `project.yml` es corto y se revisa en un PR.

**Arquitectura: MVVM en capas, como Android**

| Capa | Dónde | Responsabilidad | Puede depender de |
|------|-------|-----------------|-------------------|
| domain | paquete Swift local `Packages/ReaderDomain` | Modelos propios, protocolos de repositorio, reglas puras | nada de SwiftUI, AppKit ni UIKit |
| ui | `App/UI/` | Vistas SwiftUI, `ViewModel` (`@MainActor @Observable`), tema | domain |
| data | `App/Data/` | Implementaciones de repositorio (hoy solo `FakeLibraryRepository`) | domain |

- El dominio es un paquete aparte para que el compilador impida que dependa de la UI, y para testearlo con `swift test` sin abrir Xcode.
- Sin framework de inyección de dependencias: los `ViewModel` reciben sus repositorios por el inicializador y la app los arma en `ReaderApp`. Se revisa si el grafo crece.
- Concurrencia con `async`/`await` y el modo de lenguaje Swift 6 (concurrencia estricta).

**Tests**
- Swift Testing (`import Testing`, `#expect`), en el paquete de dominio (`swift test`) y en el target `ReaderTests` de la app (`xcodebuild test` o ⌘U en Xcode).
- Los tests citan los IDs de requisito, igual que en Android. Los casos de las reglas compartidas repiten los de Android (el spec es el contrato común).

**Firma y distribución**
- Para desarrollo, firma local ("Sign to Run Locally"): no requiere cuenta de Apple Developer.
- Sandbox activado desde el principio (la Mac App Store lo exige).
- La cuenta de Apple Developer (pago anual) se necesita para TestFlight, App Store y dispositivos iOS, no antes.

**Tipografía**
- La interfaz usa Host Grotesk (ADR 0009), que viaja en `App/Fonts` y se registra al arrancar con Core Text (sirve en Mac e iOS).

**Pendiente de decidir (con su ADR, en la rebanada que lo necesite)**
- Persistencia local (SwiftData, GRDB u otra), equivalente a Room y DataStore.
- Motor de EPUB: Readium Swift Toolkit, en línea con ADR 0004.
- SDK de Firebase para Apple, en línea con ADR 0007.
- Navegación de la Mac (barra lateral en lugar de la barra inferior de HOM-005): requiere diseño.

## Alternativas consideradas

- **Carpetas separadas para Mac (`macosx`) e iOS.** Descartado: SwiftUI comparte casi todo el código; separarlos obliga a mover código después.
- **Mac Catalyst (la app de iPad corriendo en Mac).** Descartado: SwiftUI multiplataforma da una app de Mac más natural sin perder el código compartido.
- **Proyecto de Xcode versionado tal cual.** Descartado por los conflictos y la fragilidad del `.pbxproj`.
- **Tuist en lugar de XcodeGen.** Más potente, pero más complejo para un proyecto chico. Se puede migrar si hace falta.
- **XCTest en lugar de Swift Testing.** Swift Testing es el estándar actual de Xcode 16 y permite tests parametrizados; XCTest sigue disponible si alguna herramienta lo exige.

## Consecuencias

- Hay que tener XcodeGen instalado y regenerar el proyecto al agregar archivos o cambiar `project.yml`.
- La máquina de desarrollo está en macOS 14 con Xcode 16.2, el último que corre ahí. Para publicar en las tiendas hará falta actualizar macOS y Xcode, porque Apple exige compilar con un SDK reciente.
- Sin las Command Line Tools apuntando a Xcode no hay framework de tests: `xcode-select` debe apuntar a `Xcode.app`.
