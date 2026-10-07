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
  - Al avanzar, la página actual, con su fondo, se desliza hacia la izquierda, sin cambiar su opacidad. Debajo, la página nueva entra con un desplazamiento pequeño hacia la izquierda (paralaje).
  - En el tema oscuro, el fondo de la página que sale se aclara a `#242728` de forma gradual: crece desde el comienzo y llega al máximo al 80% del recorrido, para distinguirla de la que entra.
  - Al retroceder, es la animación de avanzar reproducida al revés (como un rollback): la página anterior, con su fondo, entra deslizándose desde la izquierda por encima. Debajo, la página actual se desplaza un poco hacia la derecha (paralaje).
  - En los temas claro y sepia ocurre lo mismo pero el fondo de la página que sale se oscurece un poco (claro: de blanco a `#E6E6E6`; sepia: de `#FAF4E8` a `#E6DCC5`), con la misma curva y el mismo recorrido.
  - En el tema oscuro, al retroceder el aclarado es el mismo y se aplica igual a la página que sale del foco (la que se estaba viendo, que queda debajo): crece desde el comienzo y llega al máximo al 80% del recorrido.
  - Con un toque en los bordes la animación completa dura unos 300 ms.
  - Con un gesto de deslizar horizontal, la animación sigue al dedo: el avance de la animación es proporcional a lo que se arrastra.
  - Al soltar, si el avance pasó de un umbral (30% del recorrido) o el gesto fue una pasada rápida, la animación se completa y se pasa de página. Si no, vuelve atrás y la página queda como estaba.
  - Al soltar, el tramo que falta parte de la velocidad que llevaba el dedo y frena suavemente, sin saltos ni cortes bruscos.
  - El usuario puede desactivarla. Si está desactivada, o en modo scroll, el paso de página es inmediato.
  - Mientras dura la animación no se aceptan otros pasos de página. Excepción: si la página ya cambió y la animación solo se está asentando, tocar la pantalla la termina al instante y permite pasar de página seguido.
- **RDR-010** En modo paginado debe mostrar el número de página actual en el pie de la pantalla, fuera del texto y visible aunque los controles estén ocultos. Es la posición del libro, que no cambia con el tamaño de letra. No muestra el total. En modo scroll no se muestra.

## Escenarios

- Dado que cierro un libro a mitad, cuando lo reabro, entonces vuelvo a la misma posición.
- Dado que cambio el tema a oscuro, cuando abro la app en otro dispositivo, entonces también está oscuro.
- Dado que toco el borde derecho en modo paginado, cuando la animación está activa, entonces la página actual se desliza a la izquierda y se ve entrar la siguiente.
- Dado que arrastro la página a la izquierda más del umbral, cuando suelto el dedo, entonces la animación se completa y veo la página siguiente.
- Dado que arrastro la página solo un poco, cuando suelto el dedo, entonces la animación vuelve atrás y sigo en la misma página.
- Dado que estoy en la última página, cuando intento avanzar, entonces no hay animación ni cambio.
- Dado que desactivo la animación, cuando paso de página, entonces el cambio es inmediato.
- Dado que leo en modo paginado con los controles ocultos, cuando paso de página, entonces el número del pie cambia.
- Dado que cambio a modo scroll, entonces el número de página no se muestra.

## Fuera de alcance

Diccionario, text-to-speech, brillo propio, EPUB de maquetación fija. Otras animaciones de página, y botones de volumen para pasar de página (se evalúan después de RDR-009).
