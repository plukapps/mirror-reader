# 04 — Sincronización

## Objetivo

Mismo estado en todos los dispositivos, sin acción manual, sin perder datos.

## Requisitos

- **SYN-001** Todo cambio se guarda primero en el dispositivo y se envía solo cuando hay conexión: al importar un libro, al abrir la app, al volver a ella después de unos minutos y al iniciar sesión. Al abrir la app con el proceso nuevo siempre se sincroniza; con la app ya en marcha, a lo sumo cada 5 minutos. No hay sincronización con la app cerrada. El usuario no necesita pulsar nada.
- **SYN-002** Se sincronizan: archivos de libros, metadatos, colecciones, posición, marcadores, subrayados, notas y preferencias.
- **SYN-003** Posición: gana la lectura más reciente (la que se hizo después, no la que se envió último). Si la diferencia con la local es grande (más del 2 % del libro), la app pregunta si continuar desde la posición del otro dispositivo; si es chica, continúa desde la más reciente sin preguntar.
- **SYN-004** Marcadores y subrayados: se unen entre dispositivos, cada uno con ID único.
- **SYN-005** Notas: si dos dispositivos editan la misma nota sin conexión, se conservan ambas versiones como copia de conflicto y el usuario decide.
- **SYN-006** Metadatos y colecciones: gana el último cambio.
- **SYN-007** Los borrados se propagan mediante marcas de borrado.
- **SYN-008** Estado visible: sincronizado, pendiente o con error. Reintentos automáticos.
- **SYN-009** Opción para transferir libros solo con Wi-Fi.
- **SYN-010** Ningún conflicto puede borrar datos del usuario.
- **SYN-011** La posición de lectura se guarda al instante en el dispositivo y se envía al cerrar el libro, es decir, al salir del lector o al mandar la app a segundo plano. Un solo envío por sesión de lectura, no uno por página. Si no hay conexión, o la app se cierra sin avisar, la posición queda pendiente y se envía en la próxima oportunidad.
- **SYN-012** La posición y el porcentaje de lo leído en otros dispositivos se mantienen al día solos mientras la app está a la vista, sin esperar a la sincronización de la biblioteca. Al abrir un libro se consulta su posición más reciente antes de mostrarlo.
- **SYN-013** Si mientras se lee un libro otro dispositivo avanza ese mismo libro, se ofrece seguir desde allí con un aviso discreto. La página nunca se mueve sola.

## Escenarios

- Dado dos dispositivos sin conexión que editan la misma nota, cuando ambos se conectan, entonces existen dos versiones y el usuario elige.
- Dado que borro un subrayado en A, cuando B sincroniza, entonces desaparece en B.
- Dado que pierdo conexión durante una subida, cuando vuelve, entonces continúa sin duplicar el libro.

## Fuera de alcance

Edición colaborativa en tiempo real, compartir con otros usuarios.
