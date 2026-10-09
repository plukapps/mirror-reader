# 01 — Biblioteca

## Objetivo

Una biblioteca única y ordenada de los EPUB del usuario.

## Requisitos

- **LIB-001** Debe poder importar uno o varios EPUB desde el selector de archivos y desde "Compartir / Abrir con" de otra app.
- **LIB-002** Debe rechazar EPUB corruptos o con DRM, con un mensaje claro.
- **LIB-003** Debe identificar cada libro por el hash de su contenido. El mismo archivo importado dos veces, o desde dos dispositivos, es un solo libro.
- **LIB-004** Debe leer título, autor y portada del EPUB. El usuario puede editarlos.
- **LIB-005** Debe permitir colecciones. Un libro puede estar en varias. Todos aparecen en "Todos los libros".
- **LIB-006** Debe buscar por título, autor y colección.
- **LIB-007** Cada libro está "solo en la nube" o "descargado en este dispositivo". Con sesión, los libros de la nube se descargan solos al abrir la app; si alguno aún no está en el dispositivo, abrirlo lo descarga. El usuario puede quitar la descarga.
- **LIB-008** "Quitar del dispositivo" es distinto de "Eliminar de mi biblioteca". Eliminar afecta a todos los dispositivos y pide confirmación.
- **LIB-009** Si se supera la cuota, se bloquean nuevas importaciones. Nunca se bloquea la lectura ni se borra nada.
- **LIB-010** Debe poder filtrarse por estado de lectura: Todos, Leyendo y Terminados. Un libro sin abrir no está en "Leyendo" ni en "Terminados".
- **LIB-011** Cada libro de la grilla debe mostrar portada, título y su progreso de lectura. Un libro al 100 % se marca como terminado y uno sin abrir como nuevo.
- **LIB-012** La portada de un libro se ve en todos mis dispositivos, también antes de descargar el libro. Las portadas no cuentan para la cuota de espacio.

## Escenarios

- Dado un EPUB ya en mi biblioteca, cuando lo importo de nuevo, entonces la app avisa que ya existe y no lo duplica.
- Dado un EPUB con DRM, cuando lo importo, entonces se rechaza con explicación.
- Dado un libro "solo en la nube" sin conexión, cuando intento abrirlo, entonces la app indica que necesita conexión para descargarlo.
- Dado un libro que subí desde otro dispositivo, cuando abro la biblioteca en este, entonces veo su portada sin haberlo descargado.

- Dado un libro al 42 %, cuando abro "Leyendo", entonces aparece; cuando abro "Terminados", no.

## Fuera de alcance

Búsqueda dentro del texto, PDF, otros formatos.
