# 08 — Arranque y bienvenida

## Objetivo

Que abrir la app se sienta inmediato y con identidad propia, y que quien llega por primera vez (o sin sesión) vea de qué se trata antes de entrar. Diseño: "01 — Welcome" de `design/Margin Ebook App.dc.html` (fondo amarillo, logo, titular y botón "Get started").

## Requisitos

- **WEL-001** Al abrir la app, la pantalla de arranque del sistema debe tener el fondo amarillo de la marca y el logo de las cuatro barras en tinta, centrado.
- **WEL-002** La pantalla de arranque no debe agregar espera: se retira en cuanto la app puede dibujar, sin demora fija, animación de espera ni condición que la retenga (ni sesión, ni sincronización, ni datos).
- **WEL-003** ~~Debe mostrar la pantalla de bienvenida si nunca se completó, o si no hay sesión iniciada.~~ Reemplazado por ONB-019: la bienvenida se muestra si no hay sesión; con sesión de email sin verificar se abre la verificación; si no, Inicio (HOM-005).
- **WEL-004** ~~Reemplazado por ONB-001 (diseño O1).~~ La bienvenida seguía el diseño 01: fondo amarillo, logo y "margin." arriba a la izquierda; titular en tres líneas "Leé más. / Scrolleá menos. / Pensá despacio."; bajada "Tus EPUB, en un lugar tranquilo, en todos tus dispositivos."; botón "Comenzar" con flecha; pie "margin. 2026 ©".
- **WEL-005** ~~Reemplazado por ONB-002 y ONB-013.~~ Tocar "Comenzar" (el botón o la flecha) debe registrar la bienvenida como completada y llevar a Inicio. Volver atrás desde Inicio no regresa a la bienvenida.
- **WEL-006** Mientras se decide entre bienvenida e Inicio, la app muestra el mismo fondo amarillo, sin parpadeo de otro color ni de Inicio.
- **WEL-007** Debe funcionar en teléfono y tablet, en vertical y apaisado (el contenido no se corta y se puede desplazar si no entra), y ser compatible con TalkBack: el logo se anuncia como "margin." y cada botón con su texto (AND-001, AND-002).
- **WEL-008** Si la app se abre con un EPUB ("Abrir con"), el libro se importa y abre igual que hoy, aunque corresponda la bienvenida.

## Escenarios

> Desde el onboarding (`09-onboarding.md`) la bienvenida ya no tiene "Comenzar": se sale creando una cuenta o iniciando sesión. Los escenarios con "Comenzar" quedan como historia.

- Dado que instalo la app por primera vez, cuando la abro, entonces veo el arranque amarillo y enseguida la bienvenida.
- Dado que toqué "Comenzar" y tengo sesión, cuando vuelvo a abrir la app, entonces voy directo a Inicio.
- Dado que toqué "Comenzar" pero no tengo sesión, cuando vuelvo a abrir la app, entonces veo la bienvenida otra vez.
- Dado que estoy en la bienvenida, cuando toco "Comenzar", entonces veo Inicio, y el botón atrás sale de la app.
- Dado que estoy sin conexión, cuando abro la app, entonces el arranque no espera a la red.

## Fuera de alcance

El menú de dos rayas del diseño 01; registro e inicio de sesión (llegan con `09-onboarding.md`); varias páginas de bienvenida o tutorial; animación del logo en el arranque.
