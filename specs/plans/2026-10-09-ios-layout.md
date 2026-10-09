# Apple — Inicio en iPhone

**Objetivo:** que la prueba de Inicio (plan `2026-10-08-apple-home-spike.md`) compile y corra en iPhone y se vea completa, sin contenido cortado. Hoy el proyecto solo apunta a la Mac y la vista impone una ventana de 480 pt de ancho mínimo, más ancha que cualquier iPhone en vertical.

**Spec:** `specs/platforms/apple.md` (IOS-001, IOS-002), `specs/product/07-home.md` (HOM-001 a 003, HOM-008 a 010). Diseño: "02 — Home" de `design/Margin Ebook App.dc.html`, que ya es de teléfono.

**Rama:** `feature/ios-layout` (worktree `.claude/worktrees/ios-layout`).

## Fuera de alcance

Barra de navegación inferior (HOM-005, tarjeta aparte), iPad con diseño propio, ícono de iOS definitivo, firma para dispositivo físico con cuenta de Apple Developer (K-080), orientación horizontal pulida.

## Decisiones

- Un solo target `Reader` con dos destinos (`supportedDestinations: [macOS, iOS]` de XcodeGen), como anticipa el ADR 0010. iOS 17 como mínimo.
- Los ajustes solo de Mac (hardened runtime, sandbox por entitlements, categoría de la App Store de Mac) quedan condicionados al SDK de macOS.
- El tamaño mínimo y el tamaño inicial de la ventana pasan a `WindowLayout`, que en iOS no impone nada: el contenido toma el ancho de la pantalla.
- En iOS la interfaz se fija en modo claro (la paleta del diseño es clara y no hay tema oscuro todavía); si no, la barra de estado queda blanca sobre el fondo claro.

## Tareas

- **K-094** Target de iOS: `project.yml` con destinos Mac e iOS, ajustes condicionados por SDK, ícono universal. Verificación: `xcodebuild build` para macOS y para el simulador de iPhone.
- **K-095** Layout de Inicio en iPhone: `WindowLayout` (sin mínimo en iOS), modo claro, márgenes y tamaños revisados en un iPhone de 375 pt y en uno de 430 pt. Tests de `WindowLayout` (IOS-001). Verificación: captura en el simulador (iPhone SE/16 y 16 Pro Max) y la Mac sin cambios.

## Resultado

- Causa del corte: `.frame(minWidth: 480)` de la ventana de la Mac también se aplicaba en iPhone (393 pt), y el contenido quedaba centrado y cortado a ambos lados.
- `WindowLayout` concentra el mínimo (solo Mac), el tamaño inicial, el margen lateral y el ancho máximo. Modo claro forzado en iOS.
- Tests: 8 en iPhone 16 (simulador) y 7 en la Mac, todos pasan; 11 del dominio sin cambios.
- Verificado en el simulador con el sistema en modo oscuro: iPhone SE (375 pt), iPhone 16 (393 pt) y 16 Pro Max (440 pt), sin cortes y con la barra de estado legible. La Mac compila con sandbox.
- Pendiente: probar en un iPhone físico (requiere firmar con un Apple ID, K-080) y la barra inferior (HOM-005, K-096).
