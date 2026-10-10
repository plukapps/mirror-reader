# 09 — Onboarding: cuenta, verificación y primeros pasos

## Objetivo

Que quien llega por primera vez cree su cuenta (o entre con la que ya tiene) en pocos pasos cortos, sin fricción, y aterrice en Inicio con la app ajustada a su ritmo. Diseño: pantallas O1 a O9 de `design/Margin Ebook App.dc.html` (sección "onboarding").

Recorridos:

- **Alta:** bienvenida → crear cuenta → verificar email → intereses → meta diaria → "Ya estás adentro" → Inicio.
- **Ingreso:** bienvenida → iniciar sesión → Inicio. Desde iniciar sesión: olvidé mi contraseña → enlace por email → nueva contraseña → Inicio.

## Requisitos

### Bienvenida (O1)

- **ONB-001** La bienvenida sigue el diseño O1: fondo amarillo, logo y "margin." arriba; titular "Leé más. / Scrolleá menos. / Pensá despacio."; bajada; botón principal "Crear cuenta" con flecha; botón secundario "Ya tengo una cuenta"; aviso "Al continuar aceptás los Términos y la Política de privacidad". Reemplaza a WEL-004 y WEL-005.
- **ONB-002** "Crear cuenta" lleva al alta (O2). "Ya tengo una cuenta" lleva al ingreso (O7).

### Alta (O2 a O6)

- **ONB-003** Las pantallas del alta muestran el paso ("Paso 1 de 4" a "Paso 4 de 4") y una barra de progreso de cuatro tramos. La flecha atrás vuelve al paso anterior.
- **ONB-004** Crear cuenta (O2) permite registrarse con Google, o con nombre, email y contraseña. El botón "Crear cuenta" solo se habilita con nombre, email con formato válido, contraseña válida (ONB-005) y la casilla de Términos y Política de privacidad marcada.
- **ONB-005** Contraseña válida: al menos 8 caracteres y al menos dos de: un número, un símbolo, una mayúscula. Debajo del campo, un medidor de cuatro tramos (uno por regla cumplida: largo, número, símbolo, mayúscula) con la etiqueta "Débil", "Regular", "Buena" o "Fuerte". El campo permite ver u ocultar la contraseña.
- **ONB-006** Al crear la cuenta con email, se envía un email de verificación y se pasa a O3. Si el email ya tiene cuenta, se avisa "Ya hay una cuenta con ese email." con acceso a iniciar sesión. Con Google no hace falta verificar: se pasa directo a intereses (O4).
- **ONB-007** Verificar email (O3): "Revisá tu bandeja", el email al que se envió, "Cambiar" para corregirlo, "Abrir email" (abre la app de correo) y "Reenviar" con espera de 60 s entre envíos, con la cuenta regresiva visible. La verificación se hace con el enlace del email. La app detecta sola que se verificó (al volver a primer plano y cada pocos segundos mientras la pantalla está a la vista) y pasa a intereses.
- **ONB-008** "Cambiar" (y la flecha atrás) en O3 vuelve a crear cuenta con los datos cargados, salvo la contraseña, y descarta la cuenta recién creada sin verificar. Si la cuenta ya se verificó, en cambio, sigue a intereses.
- **ONB-009** Intereses (O4): "¿Qué te gusta leer?", "Elegí al menos 3.", chips de géneros que se marcan y desmarcan, conteo "N elegidos" y "Continuar", habilitado con 3 o más. "Omitir" pasa al paso siguiente sin guardar.
- **ONB-010** Meta diaria (O5): "¿Cuánto querés leer por día?" con cuatro opciones (10 min Tranquilo, 20 min Regular · ~12 libros/año, 30 min En serio, 1 hora Devoto), una sola elegida (20 min por defecto); recordatorio diario con interruptor y hora (21:30 por defecto, se cambia tocándola); "Terminar". "Omitir" pasa al final sin guardar meta ni recordatorio.
- **ONB-011** Si el recordatorio queda activado, la app pide permiso de notificaciones cuando el sistema lo exige. Si se niega, la configuración se guarda igual pero el recordatorio queda apagado.
- **ONB-012** Recordatorio diario: con el recordatorio activado, la app muestra una notificación por día cerca de la hora elegida ("Es hora de leer" y la meta del día). Tocarla abre la app. Sobrevive al reinicio del dispositivo.
- **ONB-013** "Ya estás adentro" (O6): fondo amarillo, "Ya estás adentro, [nombre]." y un resumen de la meta y el recordatorio elegidos (o una frase genérica si se omitieron). "Empezar a leer" lleva a Inicio; "Importar mis libros" lleva a Inicio y abre el selector de EPUB. Atrás desde Inicio sale de la app, sin volver al onboarding.

### Ingreso (O7 a O9)

- **ONB-014** Iniciar sesión (O7): "Te damos la bienvenida de nuevo.", email y contraseña (con ver u ocultar), "¿Olvidaste tu contraseña?", "Iniciar sesión", Google, y "¿Nuevo en margin.? Crear cuenta".
- **ONB-015** Si el ingreso falla, el error se muestra bajo el campo de contraseña en rojo, con un texto claro: email o contraseña incorrectos, cuenta deshabilitada, demasiados intentos o sin conexión. La app no revela si un email tiene cuenta.
- **ONB-016** Al iniciar sesión con una cuenta de email sin verificar, se envía el email de verificación y se pasa a verificar (O3). Si no, a Inicio, sin poder volver atrás al ingreso.
- **ONB-017** Olvidé mi contraseña (O8): email (precargado con el de O7), "Enviar enlace"; luego "Enlace enviado / ¿No llegó? Revisá spam.", "Abrir app de correo" y "Reenviar" con espera de 30 s. Se responde igual exista o no la cuenta.
- **ONB-018** Nueva contraseña (O9): al abrir el enlace del email en el dispositivo, la app muestra "Elegí una contraseña nueva" para el email de la cuenta, con las reglas de ONB-005 marcadas a medida que se cumplen, y "Confirmar contraseña". "Guardar e ingresar" se habilita con contraseña válida y ambas iguales; guarda, inicia sesión y abre Inicio. Si el enlace venció o ya se usó, lo dice y ofrece pedir otro. La cruz cierra y vuelve a la bienvenida (o a donde estaba).

### Arranque y comunes

- **ONB-019** Al abrir la app: sin sesión, bienvenida (O1); con sesión de email sin verificar, verificar (O3); si no, Inicio. Reemplaza la regla de WEL-003.
- **ONB-020** Con Google, si el usuario cancela la elección de cuenta no se muestra error; si falla, se avisa en la pantalla.
- **ONB-021** Mientras una acción espera al servidor, su botón muestra que está trabajando y no se puede tocar de nuevo.
- **ONB-022** Todas las pantallas funcionan en teléfono y tablet, vertical y apaisado (se desplazan si no entran, columna de ancho máximo en tablet), con el teclado abierto sin tapar el botón principal, y con TalkBack (cada control con su nombre, los chips y opciones con su estado).

## Escenarios

- Dado que instalo la app, cuando toco "Crear cuenta", completo nombre, email y contraseña, acepto los términos y toco "Crear cuenta", entonces veo "Revisá tu bandeja" con mi email.
- Dado que estoy en "Revisá tu bandeja", cuando abro el enlace del email y vuelvo a la app, entonces paso solo a "¿Qué te gusta leer?".
- Dado que elijo 3 géneros, 30 min y recordatorio a las 22:00, cuando toco "Terminar", entonces veo "Ya estás adentro, Ana." con "30 minutos por día, recordatorio a las 22:00".
- Dado que cierro la app en "Revisá tu bandeja" sin verificar, cuando la vuelvo a abrir, entonces veo "Revisá tu bandeja" otra vez.
- Dado que ingreso con una contraseña equivocada, cuando toco "Iniciar sesión", entonces veo "Email o contraseña incorrectos." bajo la contraseña.
- Dado que pedí el enlace para cambiar la contraseña, cuando lo abro en el teléfono y guardo una nueva, entonces entro a Inicio con la sesión iniciada.
- Dado que toco Google y cierro el selector de cuentas, entonces sigo en la misma pantalla sin error.

## Fuera de alcance

Uso sin cuenta ("Explorar sin cuenta" y la variante O1 v2), "Iniciar sesión con Apple" (llega con iOS, ACC), ingreso con huella, códigos de 6 dígitos por email (se usa el enlace), "60.000 clásicos gratis" y los libros sugeridos de O6 (no hay catálogo), sincronizar intereses y meta entre dispositivos, medir el tiempo de lectura contra la meta, cerrar sesión y perfil (ACC-006).
