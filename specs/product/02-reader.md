# 02 — Lector

## Objetivo

Leer cómodo, sin que la interfaz estorbe.

## Requisitos

- **RDR-001** Debe ofrecer modo paginado y modo scroll.
- **RDR-002** Debe permitir ajustar tamaño y tipo de letra (conjunto corto), interlineado y márgenes.
- **RDR-003** Debe ofrecer temas claro, oscuro y sepia.
- **RDR-004** Debe mostrar tabla de contenidos y permitir saltar a un capítulo o a un porcentaje.
- **RDR-005** Debe mostrar el progreso de lectura.
- **RDR-006** Debe guardar la posición automáticamente (ubicación exacta en el EPUB y porcentaje).
- **RDR-007** Debe funcionar sin conexión para libros descargados.
- **RDR-008** Las preferencias de lectura (letra, tema) se sincronizan con la cuenta.
- **RDR-009** En modo paginado, el paso de página debe animarse así (animación "deslizar con paralaje"):
  - Al avanzar, la página actual, con su fondo, se desliza hacia la izquierda y baja un poco su opacidad. Debajo, la página nueva entra con un desplazamiento pequeño hacia la izquierda (paralaje).
  - Al retroceder, es la misma animación en sentido inverso: la página actual se desliza hacia la derecha y la anterior entra con paralaje hacia la derecha.
  - La animación dura poco (unos 300 ms) y se dispara con un toque en los bordes y con un gesto de deslizar horizontal.
  - El usuario puede desactivarla. Si está desactivada, o en modo scroll, el paso de página es inmediato.
  - Mientras dura la animación no se aceptan otros pasos de página.

## Escenarios

- Dado que cierro un libro a mitad, cuando lo reabro, entonces vuelvo a la misma posición.
- Dado que cambio el tema a oscuro, cuando abro la app en otro dispositivo, entonces también está oscuro.
- Dado que toco el borde derecho en modo paginado, cuando la animación está activa, entonces la página actual se desliza a la izquierda y se ve entrar la siguiente.
- Dado que estoy en la última página, cuando intento avanzar, entonces no hay animación ni cambio.
- Dado que desactivo la animación, cuando paso de página, entonces el cambio es inmediato.

## Fuera de alcance

Diccionario, text-to-speech, brillo propio, EPUB de maquetación fija. Otras animaciones de página, botones de volumen para pasar de página y que la página siga al dedo mientras se arrastra (se evalúan después de RDR-009).
