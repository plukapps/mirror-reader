# 04 — Sincronización

## Objetivo

Mismo estado en todos los dispositivos, sin acción manual, sin perder datos.

## Requisitos

- **SYN-001** Todo cambio se guarda primero en el dispositivo y se envía cuando hay conexión.
- **SYN-002** Se sincronizan: archivos de libros, metadatos, colecciones, posición, marcadores, subrayados, notas y preferencias.
- **SYN-003** Posición: gana la más reciente. Si la diferencia con la local es grande, la app pregunta si continuar desde la posición del otro dispositivo.
- **SYN-004** Marcadores y subrayados: se unen entre dispositivos, cada uno con ID único.
- **SYN-005** Notas: si dos dispositivos editan la misma nota sin conexión, se conservan ambas versiones como copia de conflicto y el usuario decide.
- **SYN-006** Metadatos y colecciones: gana el último cambio.
- **SYN-007** Los borrados se propagan mediante marcas de borrado.
- **SYN-008** Estado visible: sincronizado, pendiente o con error. Reintentos automáticos.
- **SYN-009** Opción para transferir libros solo con Wi-Fi.
- **SYN-010** Ningún conflicto puede borrar datos del usuario.

## Escenarios

- Dado dos dispositivos sin conexión que editan la misma nota, cuando ambos se conectan, entonces existen dos versiones y el usuario elige.
- Dado que borro un subrayado en A, cuando B sincroniza, entonces desaparece en B.
- Dado que pierdo conexión durante una subida, cuando vuelve, entonces continúa sin duplicar el libro.

## Fuera de alcance

Edición colaborativa en tiempo real, compartir con otros usuarios.
