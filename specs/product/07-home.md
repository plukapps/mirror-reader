# 07 — Inicio

## Objetivo

Una pantalla de inicio que lleva al usuario de vuelta a su lectura en un toque. Diseño: "02 — Home" de `design/Margin Ebook App.dc.html`.

## Requisitos

- **HOM-001** Debe mostrar un saludo según la hora del día (mañana, tarde, noche). Mientras no exista cuenta (ACC), el saludo no lleva nombre ni avatar.
- **HOM-002** Debe mostrar "Continuar leyendo" con el libro abierto más recientemente que esté en lectura (no nuevo ni terminado): portada, título, autor, barra de progreso y porcentaje. Tocarlo abre el libro en su última posición.
- **HOM-003** Si no hay ningún libro en lectura, en lugar de "Continuar leyendo" debe invitar a abrir la biblioteca o a importar un libro. Si la biblioteca está vacía, ofrece importar (LIB-001).
- **HOM-004** Retirado: "Para ti" se reemplaza por HOM-008 a HOM-010 (el diseño cambió). No se reutiliza el ID.
- **HOM-005** Debe tener una barra de navegación inferior con cuatro destinos: Inicio, Buscar, Estantes y Perfil. Inicio es la pantalla de arranque. "Estantes" lleva a la biblioteca.
- **HOM-006** Los destinos cuya función aún no existe (Buscar, Perfil) deben ser visibles y, al tocarlos, mostrar un aviso breve de que llegan más adelante. No navegan.
- **HOM-007** Debe funcionar en teléfono y tablet y ser compatible con TalkBack (AND-001, AND-002).
- **HOM-008** Debe mostrar "Leyendo": los libros en lectura salvo el de "Continuar leyendo", el abierto más recientemente primero, hasta 5. Cada uno con portada, título, autor y progreso. El título lleva el total. Oculta si no hay.
- **HOM-009** Debe mostrar "Agregados recientemente": los últimos 5 libros importados, sin importar su estado, con portada, título y autor. El título lleva el total de la biblioteca. Oculta si la biblioteca está vacía.
- **HOM-010** Debe mostrar "Terminados": los últimos 5 libros terminados, el más reciente primero, con portada marcada con un check, título y "Apellido · Mes" (apellido del primer autor y mes de la última lectura). El título lleva el total. Oculta si no hay.
- **HOM-011** Cada sección tiene "Ver todo", que abre la biblioteca con el filtro de esa sección: Leyendo, Todos o Terminados (LIB-010). Tocar un libro lo abre en su última posición.

## Escenarios

- Dado un libro al 42 % abierto ayer y otro al 10 % abierto hoy, cuando entro a Inicio, entonces "Continuar leyendo" muestra el de hoy.
- Dado que solo tengo libros nuevos o terminados, cuando entro a Inicio, entonces veo la invitación de HOM-003 y "Agregados recientemente" con ellos.
- Dado que toco "Buscar", entonces veo el aviso y sigo en Inicio.
- Dado un libro al 42 % que es el último abierto, cuando entro a Inicio, entonces está en "Continuar leyendo" y no en "Leyendo".
- Dado que termino un libro, cuando vuelvo a Inicio, entonces pasa de "Leyendo" a "Terminados".
- Dado que importo un libro, cuando entro a Inicio, entonces es el primero de "Agregados recientemente".
- Dado que toco "Ver todo" en "Terminados", entonces veo la biblioteca en la pestaña Terminados.
- Dado que termino de leer el libro de "Continuar leyendo" (100 %), cuando vuelvo a Inicio, entonces ya no aparece ahí.

## Fuera de alcance

Sugeridos o catálogo de libros gratis (requiere contenido y backend, ADR 0003), nombre y avatar del usuario (ACC), búsqueda (LIB-006), perfil, "minutos restantes" y capítulo actual del diseño (no hay dato fiable).
