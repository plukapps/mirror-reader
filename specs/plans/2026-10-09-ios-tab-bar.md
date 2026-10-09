# Plan — Barra inferior en iPhone (Apple)

**Objetivo:** la misma barra inferior que Android (K-093, plan `2026-10-09-android-tab-bar.md`), en iPhone: igual aspecto y mismo comportamiento.

**Specs que implementa:** HOM-005, HOM-006, HOM-007 (`specs/product/07-home.md`), IOS-001 (`specs/platforms/apple.md`).
**Rama:** `feature/ios-tab-bar`. **Tarjeta:** K-096. Pedido del usuario (2026-10-09).

## Diseño (igual que Android)

- Píldora flotante: alto 60 pt, radio 30 pt, fondo tinta `#130000`, margen 40 pt a los lados y 12 pt abajo (sobre el área segura), relleno horizontal 8 pt, íconos repartidos.
- Solo íconos Material Symbols Rounded de 24 pt (home, search, shelves, person). Inactivo: contorno en `#8A8676`. Activo: relleno, en tinta, sobre una píldora amarilla `#FBD256` de 52 × 44 pt.
- La barra flota sobre el contenido y el contenido pasa por debajo, sin fondo alrededor, sin translucidez ni sombra. Al final del scroll queda el espacio de la barra para que lo último se vea.

## Decisiones

- **Barra propia, no `TabView`:** la barra del sistema no se puede dibujar como el diseño. La tarjeta K-096 nombraba `TabView`; se descarta por eso.
- **Íconos:** los mismos trazados de Material Symbols que Android, como SVG vectoriales en `Assets.xcassets` (plantilla, se tiñen con el color).
- **Espacio al final:** la barra va en `safeAreaInset(edge: .bottom)`; el scroll pasa por debajo y suma ese alto al final de su contenido.
- **Destinos:** en Apple solo existe Inicio. Buscar, Estantes y Perfil muestran "Llega más adelante." (HOM-006) hasta que existan; Estantes navega a la biblioteca cuando esta llegue (K-079). El aviso es una píldora tinta breve sobre la barra (iOS no tiene toast).
- **Solo iPhone:** en la Mac no hay barra; su navegación sigue pendiente (K-078).
- **Accesibilidad (HOM-007):** cada destino es un botón con su nombre para VoiceOver y el rasgo de seleccionado.

## Tareas

### Tarea 1: barra y navegación (S)
- [x] `MainDestination` y `TabBarModel` (`@Observable`): destino actual y aviso de los que no existen.
- [x] Íconos SVG en el asset catalog; `MarginTabBar` con la píldora.
- [x] `RootView`: Inicio con la barra en iPhone, sin barra en la Mac.
- [x] Tests (Swift Testing) del modelo: HOM-005, HOM-006.
**Verificación:** tests en iPhone y Mac; a ojo en el simulador (arriba, a mitad de scroll y al final).

## Resultado

Tests: 13 en iPhone, 12 en la Mac y 11 del dominio, todos pasan. En el simulador (iPhone 16) la barra se ve como en Android, el contenido pasa por debajo, al final del scroll lo último queda entero sobre la barra y el aviso aparece encima de la barra. El final del scroll y el aviso se forzaron con un cambio temporal (ya revertido), porque no había forma de tocar el simulador. Falta probarlo a mano en un iPhone.
