# Plan — Barra inferior según el diseño (Android)

**Objetivo:** que la barra de navegación inferior se vea tal cual el diseño (`design/Margin Ebook App.dc.html`, pantallas 02 Home, 03 Search y 05 Library).

**Specs que implementa:** HOM-005, HOM-006, HOM-007 (`specs/product/07-home.md`). No cambia el comportamiento: solo la apariencia.
**Rama:** `feature/tab-bar`. **Tarjeta:** K-093. Pedido del usuario (2026-10-09).

## Diseño (medidas del HTML)

- Píldora flotante: alto 60 dp, radio 30 dp, fondo tinta `#130000`, margen 40 dp a los lados y 12 dp abajo, relleno horizontal 8 dp, íconos repartidos con `space-around`.
- Sin texto: solo íconos Material Symbols Rounded de 24 dp (home, search, shelves, person).
- Inactivo: ícono contorno, color `#8A8676`.
- Activo: píldora amarilla `#FBD256` de 52 × 44 dp (radio 22 dp), ícono relleno (`FILL 1`) en tinta.
- Alrededor de la barra, el fondo de la pantalla (`Paper`); sin línea divisoria.

## Decisiones

- **Íconos:** trazados de Material Symbols Rounded (peso 400, opsz 24, versión contorno y rellena) copiados como `ImageVector`, sin sumar la librería de íconos.
- **Accesibilidad (HOM-007):** sin texto visible, cada destino lleva su nombre como descripción para TalkBack y conserva el estado seleccionado y el rol de pestaña.
- **Tablet:** la misma barra. El riel lateral de T01 queda fuera.

## Tareas

### Tarea 1: barra nueva (S)
- [x] `NavIcons` con los trazados de Material Symbols Rounded (contorno y relleno).
- [x] `MarginBottomBar` con la píldora flotante, sin etiquetas, con descripción accesible.
- [x] Test de emulador (HOM-005, HOM-007): cuatro destinos con nombre accesible, uno seleccionado.
**Verificación:** compila, tests JVM pasan, test de emulador compila; a ojo en el teléfono.

## Resultado

Compila y los tests JVM pasan. El test de emulador (`MarginBottomBarTest`, 3 casos) compila, pero no se corrió. Instalado en el teléfono: Inicio y Estantes se ven como en el diseño (2026-10-09).
