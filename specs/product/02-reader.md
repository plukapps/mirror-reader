# 02 — Lector

## Objetivo

Leer cómodo, sin que la interfaz estorbe.

## Requisitos

- **RDR-001** La lectura es siempre paginada. No hay modo scroll (decidido el 2026-10-08).
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
  - El usuario puede desactivarla. Si está desactivada, el paso de página es inmediato.
  - En la primera página del libro no se puede retroceder, y en la última no se puede avanzar: ni el deslizar ni el toque en el borde mueven ni animan nada.
  - Mientras dura la animación no se aceptan otros pasos de página. Excepción: si la página ya cambió y la animación solo se está asentando, tocar la pantalla la termina al instante y permite pasar de página seguido.
- **RDR-010** En modo paginado debe mostrar el número de página actual en el pie de la pantalla, fuera del texto y visible aunque los controles estén ocultos. Es la posición del libro, que no cambia con el tamaño de letra. No muestra el total.
- **RDR-011** En modo paginado, un toque en el 20% izquierdo o derecho de la pantalla pasa de página, y en el 60% central muestra u oculta los controles. Una pulsación larga (selección de texto) nunca pasa de página, ni sus arrastres.
- **RDR-012** Al leer, cuando el usuario pasa del cuerpo del libro a sus páginas finales (índice, notas, bibliografía, glosario, agradecimientos, sobre el autor, créditos), debe avisarle con un mensaje breve ("Parece que terminaste el libro"). El fin del cuerpo se deduce de la tabla de contenidos: es el comienzo del último bloque de entradas de primer nivel cuyo título contiene alguna de esas palabras (en español o inglés, en singular o plural), incluso si el libro cierra con una promoción de otros libros. El aviso sale una vez por apertura del libro y no sale si el libro se reabre ya dentro de esas páginas. Si el libro no tiene ese bloque, no hay aviso. Por ahora el aviso no cambia el progreso ni el estado del libro.
- **RDR-013** Al mostrarse los controles (RDR-011), arriba aparece una barra con tres elementos: un botón para volver a la pantalla anterior, al centro el título del capítulo donde está el lector (vacío si el libro no tiene tabla de contenidos), y a la derecha el botón "Aa" que abre el panel de ajustes (RDR-014). El botón "Aa" se ve activo mientras el panel está abierto. Marcadores y notas no están en esta barra por ahora.
- **RDR-015** Mientras los controles están ocultos (RDR-011), la barra de estado y la barra de navegación del sistema también se ocultan, y el texto del libro no se mueve ni se vuelve a paginar. Al mostrar los controles reaparecen. Si el usuario desliza desde el borde, las barras se asoman un momento y se vuelven a ocultar. Al salir del lector se muestran de nuevo.
- **RDR-014** El panel de ajustes de lectura es un panel que sube desde abajo y deja ver el libro detrás. Contiene, en este orden:
  - **Tipo de letra:** tres opciones (con serifa, sin serifa y monoespaciada), la elegida resaltada. Cada opción se muestra con su propia tipografía.
  - **Tamaño:** botones "A−" y "A+" (RDR-002).
  - **Tema:** Clásico, Sepia y Noche, cada uno como un círculo del color de su fondo con su nombre debajo; el elegido resaltado (RDR-003).
  - **Interlineado:** dos opciones, normal y amplio, la elegida resaltada.
  - **Lectura:** acceso al índice (RDR-004) y la animación de paso de página (RDR-009).
  Cada cambio se aplica en el momento sobre el libro que se ve detrás y se guarda como preferencia de lectura. El panel se cierra tocando el libro fuera de él, arrastrándolo hacia abajo, con el gesto o botón atrás del sistema o volviendo a tocar "Aa". Mientras está abierto, tocar el libro no pasa de página. Abrir el índice desde el panel lo cierra.
  Nota de alcance: el brillo propio y los márgenes no forman parte de este panel todavía.

## Escenarios

- Dado que cierro un libro a mitad, cuando lo reabro, entonces vuelvo a la misma posición.
- Dado que cambio el tema a oscuro, cuando abro la app en otro dispositivo, entonces también está oscuro.
- Dado que toco el borde derecho en modo paginado, cuando la animación está activa, entonces la página actual se desliza a la izquierda y se ve entrar la siguiente.
- Dado que arrastro la página a la izquierda más del umbral, cuando suelto el dedo, entonces la animación se completa y veo la página siguiente.
- Dado que arrastro la página solo un poco, cuando suelto el dedo, entonces la animación vuelve atrás y sigo en la misma página.
- Dado que estoy en la última página, cuando intento avanzar, entonces no hay animación ni cambio.
- Dado que estoy en la primera página, cuando intento retroceder (deslizando o tocando el borde), entonces no hay animación ni cambio.
- Dado que desactivo la animación, cuando paso de página, entonces el cambio es inmediato.
- Dado que leo en modo paginado con los controles ocultos, cuando paso de página, entonces el número del pie cambia.
- Dado que toco el centro de la pantalla en modo paginado, entonces se muestran u ocultan los controles y no cambia la página.
- Dado que los controles están visibles, cuando toco el botón de volver, entonces regreso a la pantalla desde la que abrí el libro.
- Dado que estoy leyendo el capítulo "Libro II", cuando se muestran los controles, entonces la barra superior dice "Libro II"; cuando paso al capítulo siguiente, el título cambia.
- Dado que toco "Aa", entonces sube el panel de ajustes con el libro visible detrás, y "Aa" se ve activo.
- Dado que el panel está abierto, cuando elijo Sepia, entonces el libro de atrás cambia a sepia al instante y, al cerrar y reabrir el libro, sigue en sepia.
- Dado que elijo la tipografía monoespaciada, entonces el texto del libro se ve en esa tipografía y la opción queda resaltada.
- Dado que el panel está abierto, cuando toco el borde derecho del libro, entonces se cierra el panel y la página no cambia.
- Dado que abro el índice desde el panel, cuando elijo un capítulo, entonces el panel se cierra y el libro salta a ese capítulo.
- Dado que mantengo el dedo sobre una palabra y arrastro para seleccionar texto, entonces la página no cambia.
- Dado que un libro termina su cuerpo y sigue con "Notas" e "Índice", cuando paso a la primera página de "Notas", entonces veo el aviso una sola vez.
- Dado que reabro el libro y mi posición ya está en "Índice", entonces no veo el aviso.

## Fuera de alcance

Modo scroll (descartado), diccionario, text-to-speech, brillo propio (pendiente de decidir), EPUB de maquetación fija. Otras animaciones de página, y botones de volumen para pasar de página (se evalúan después de RDR-009).
