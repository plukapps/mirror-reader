# 07 — Inicio

## Objetivo

Una pantalla de inicio que lleva al usuario de vuelta a su lectura en un toque. Diseño: "02 — Home" de `design/Margin Ebook App.dc.html`.

## Requisitos

- **HOM-001** Debe mostrar un saludo según la hora del día (mañana, tarde, noche). Mientras no exista cuenta (ACC), el saludo no lleva nombre ni avatar.
- **HOM-002** Debe mostrar "Continuar leyendo" con el libro abierto más recientemente que esté en lectura (no nuevo ni terminado): portada, título, autor, barra de progreso y porcentaje. Tocarlo abre el libro en su última posición.
- **HOM-003** Si no hay ningún libro en lectura, en lugar de "Continuar leyendo" debe invitar a abrir la biblioteca o a importar un libro. Si la biblioteca está vacía, ofrece importar (LIB-001).
- **HOM-004** Debe mostrar "Para ti": una fila horizontal con libros de la propia biblioteca del usuario que aún no abrió (estado nuevo), del más reciente al más antiguo. Tocar uno lo abre. "Ver todo" lleva a la biblioteca. Si no hay libros nuevos, la sección no se muestra. La app no ofrece contenido propio.
- **HOM-005** Debe tener una barra de navegación inferior con cuatro destinos: Inicio, Buscar, Estantes y Perfil. Inicio es la pantalla de arranque. "Estantes" lleva a la biblioteca.
- **HOM-006** Los destinos cuya función aún no existe (Buscar, Perfil) deben ser visibles y, al tocarlos, mostrar un aviso breve de que llegan más adelante. No navegan.
- **HOM-007** Debe funcionar en teléfono y tablet y ser compatible con TalkBack (AND-001, AND-002).

## Escenarios

- Dado un libro al 42 % abierto ayer y otro al 10 % abierto hoy, cuando entro a Inicio, entonces "Continuar leyendo" muestra el de hoy.
- Dado que solo tengo libros nuevos o terminados, cuando entro a Inicio, entonces veo la invitación de HOM-003 y "Para ti" con los nuevos.
- Dado que toco "Buscar", entonces veo el aviso y sigo en Inicio.
- Dado que termino de leer el libro de "Continuar leyendo" (100 %), cuando vuelvo a Inicio, entonces ya no aparece ahí.

## Fuera de alcance

Recomendaciones o catálogo de libros gratis (requiere contenido y backend, ADR 0003), nombre y avatar del usuario (ACC), búsqueda (LIB-006), perfil, "minutos restantes" y capítulo actual del diseño (no hay dato fiable).

## Preguntas abiertas

- HOM-004 reinterpreta "Picked for you" del diseño como libros nuevos de la biblioteca propia. Confirmar o sustituir.
