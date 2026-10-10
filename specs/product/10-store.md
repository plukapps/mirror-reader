# 10 — Tienda (prueba de concepto)

> **Estado: prueba de concepto.** Valida la experiencia de "busco, obtengo y leo sin salir de la app". La compra es simulada. Si se decide seguir, este spec se revisa y la fuente comercial y el cobro se deciden en ADR propios.

## Objetivo

Que el usuario pueda encontrar lecturas nuevas y sumarlas a su biblioteca desde la app, como alternativa a importar un EPUB que ya tiene. Sin pasos manuales: elegir el libro, obtenerlo (gratis o comprándolo) y que aparezca en la biblioteca listo para leer.

Todo libro que se ofrece se obtiene de forma legal: de dominio público o, más adelante, vendido con licencia de la editorial. La app nunca ofrece quitar DRM ni importar libros protegidos de otras tiendas (LIB-002).

Diseño: pantalla "03 — Search" de `design/Margin Ebook App.dc.html`, que ya mezcla libros de la biblioteca ("En biblioteca · 42 %") con libros del catálogo ("Gratis" y el botón "Obtener") y tiene los filtros "Todo", "En mi biblioteca", "Gratis" y "Autores".

## Requisitos

- **STO-001** La pantalla Buscar busca también en la tienda. Los resultados muestran primero los libros de mi biblioteca (LIB-013 a LIB-015) y después los del catálogo que no tengo. Los del catálogo aparecen mientras escribo, tras una pausa corta.
- **STO-002** Cada resultado del catálogo muestra portada, título, autor y cómo obtenerlo: "Gratis" con el botón "Obtener", el precio con el botón "Comprar", o "No disponible en digital" sin botón cuando no hay una edición que se pueda obtener legalmente.
- **STO-003** "Obtener" descarga el libro y lo agrega a la biblioteca sin más pasos: sin salir de la app ni importar un archivo. Mientras tanto el resultado muestra que se está descargando. Al terminar dice "En tu biblioteca" y tocarlo abre el libro. Si falla, se avisa y se puede reintentar.
- **STO-004** "Comprar" muestra una confirmación con portada, título, autor y precio. Al confirmar, el libro se entrega como en STO-003. En la prueba de concepto la compra es simulada: no se cobra nada y la confirmación lo dice.
- **STO-005** Solo se ofrecen libros sin DRM que se pueden obtener legalmente. En la prueba de concepto, solo dominio público. Algunos de ellos llevan un precio de prueba para poder recorrer el flujo de compra.
- **STO-006** Filtros: "Todo" (biblioteca y catálogo), "En mi biblioteca" (solo la búsqueda local, LIB-015), "Gratis" (solo libros del catálogo que se obtienen gratis) y "Autores" (biblioteca y catálogo, buscando solo por autor).
- **STO-007** Sin conexión, o si la tienda no responde, la búsqueda en la biblioteca funciona igual (LIB-013) y se ve un aviso de que la tienda no está disponible.
- **STO-008** Un libro obtenido de la tienda aparece como "En tu biblioteca" en las búsquedas siguientes; tocarlo abre el libro. Si el mismo libro ya estaba en la biblioteca, no se duplica (LIB-003).

## Fuera de alcance (prueba de concepto)

- Cobro real y catálogo comercial con licencia (ver `specs/open-questions.md`).
- Página de detalle del libro (pantalla 04).
- Préstamo de bibliotecas.
- iPhone y Mac.

## Escenarios

**Obtener un libro gratis**
- Dado que busco "frankenstein" con conexión,
- cuando toco "Obtener" en *Frankenstein*, de Mary Shelley,
- entonces el resultado muestra que se descarga, al terminar dice "En tu biblioteca" y el libro está en la biblioteca.

**Comprar un libro (simulado)**
- Dado un resultado con precio de prueba,
- cuando toco "Comprar" y confirmo,
- entonces no se cobra nada y el libro se descarga y entra a la biblioteca como en STO-003.

**Sin conexión**
- Dado que no hay conexión,
- cuando busco,
- entonces veo los libros de mi biblioteca que coinciden y un aviso de que la tienda no está disponible.
