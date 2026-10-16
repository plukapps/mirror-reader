# 13 — Uso sin cuenta

## Objetivo

Que quien instala la app pueda importar y leer sus libros enseguida, sin registrarse. La cuenta deja de ser la puerta de entrada y pasa a ser lo que se agrega para tener la biblioteca en todos los dispositivos. Decisión del usuario (2026-10-15): bajar la fricción del primer uso para validar el producto. Resuelve open-questions #7 y cambia ACC-001.

Sin cuenta todo vive en este dispositivo: nada se sube, nada se baja y no hay nada en la nube a nombre de nadie. No hay cuentas provisorias ni anónimas en la nube.

Diseño: sección "5a — sin cuenta" de `design/Margin Ebook App.dc.html`: O1 v2, L0, H0 (banner "6b"), H0b (banner "6e"), L1, L2, L3, 09g, la variante B del alta corta (B1 a B3) y T05 en tablet. Diferencias decididas con el usuario (2026-10-15):

- **L0 no regala libros.** La app no trae contenido (visión): el texto invita a importar. Las tres portadas del diseño quedan como ilustración (pedido del usuario, 2026-10-16), no como libros.
- **El banner B dice "Tus libros sin respaldo"** y no "5 libros sin respaldo" (pedido del usuario, 2026-10-16).
- **Sin código de 6 dígitos.** B2 no se construye: la verificación sigue siendo con el enlace del email (O3, ONB-007).
- **Se quitan los intereses (O4)** de todo el alta, no solo del alta corta.
- **Sin "Iniciar sesión con Apple"** en Android (B1 lo muestra; llega con iOS, K-168).

Primera rebanada: Android (teléfono y tablet). iPhone después, con su propio plan. La Mac queda afuera, como Perfil (K-078).

## Requisitos

### Bienvenida y arranque

- **GST-001** La bienvenida (O1) sigue el diseño O1 v2: fondo amarillo, logo y "tuck.", titular "Leé más. / Scrolleá menos. / Pensá despacio.", bajada "Tus EPUB, en un lugar tranquilo, en todos tus dispositivos.", botón principal "Empezar a leer" con flecha y, debajo, "¿Querés sincronizar?" con "Crear cuenta" · "Iniciar sesión". Al pie, el aviso de Términos y Política de privacidad. Cambia ONB-001 y ONB-002.
- **GST-002** "Empezar a leer" abre "Listo" (GST-019) y deja registrado en este dispositivo que ya se pasó por la bienvenida. "Crear cuenta" lleva al alta completa (O2) e "Iniciar sesión" al ingreso (O7), como hoy.
- **GST-019** "Listo" (L0), sin cuenta: fondo amarillo, "Ya podés / leer.", "Empezá importando tus libros. Todo queda en este dispositivo, sin cuenta.", "Empezar a leer" (abre Inicio) e "Importar mis libros" (abre Inicio y el selector de EPUB, como ONB-013). Entre el texto y los botones, las tres portadas inclinadas del diseño como ilustración: no son libros de la biblioteca, no se tocan y el lector de pantalla no las anuncia. Atrás desde Inicio sale de la app.
- **GST-003** Al abrir la app: con sesión de email sin verificar, verificar (O3); con sesión, Inicio; sin sesión, Inicio si ya se pasó por la bienvenida en este dispositivo, y la bienvenida si no. Reemplaza a ONB-019. La pantalla de arranque sigue sin esperar nada (WEL-002).
- **GST-004** Quien ya tenía la app con sesión no ve ningún cambio al actualizar: sigue entrando a Inicio con su cuenta.

### Qué funciona sin cuenta

- **GST-005** Sin cuenta funciona todo lo que no necesita la nube: importar (también "Abrir con", WEL-008), la biblioteca y sus acciones, leer, la posición de lectura, las notas, buscar, estantes, Inicio, los ajustes de lectura y la apariencia. Todo se guarda solo en este dispositivo.
- **GST-006** Sin cuenta no hay sincronización: no se muestra la línea de estado de la sincronización (SYN-008), ni errores, ni "pendiente", ni "Esperando Wi-Fi". Las hojas de cuota y de archivo muy grande (PLN-007, PLN-008, PLN-014) y el aviso del 80 % (PLN-011) no se muestran, porque nada se sube. No hay tope de espacio ni de tamaño por archivo en el dispositivo.
- **GST-007** Sin cuenta valen las funciones de Free: temas y fuentes pagos muestran la hoja de función paga (PLN-015), como en una cuenta Free. Así, crear una cuenta nunca quita algo que se tenía.
- **GST-008** "Quitar de la biblioteca" (LIB-020) sin cuenta borra el libro de este dispositivo. La confirmación (L3) dice "Se borra el libro de este dispositivo, con su progreso y sus notas." y no habla de la nube ni de otros dispositivos.
- **GST-020** Lo que se quita sin cuenta no se borra nunca de la nube: si después se inicia sesión en una cuenta que tiene ese mismo libro, el libro sigue en la cuenta y vuelve a aparecer en la biblioteca.

### Invitar a crear la cuenta

- **GST-009** Perfil sin cuenta (09g): la tarjeta amarilla de arriba (PRF-005) muestra el logo en el círculo, "Sin cuenta" y "Creá una cuenta para tener tus libros en todos tus dispositivos.", con flecha. Tocarla abre el alta corta (GST-011). En la sección "Cuenta", una sola fila: "Ya tengo una cuenta", que abre el ingreso (O7). No se muestran el espacio (PRF-006), "Cambiar contraseña", "Cerrar sesión" ni "Eliminar cuenta". En "App" no se muestra "Sincronizar solo con Wi-Fi". Los ajustes de lectura, el resto de "App" y la ayuda se ven como siempre.
- **GST-010** Sin cuenta, Inicio (el banner de GST-023, debajo del saludo) y la Biblioteca (L1, arriba de la lista) muestran el aviso "Tus libros están solo en este dispositivo." / "Creá una cuenta para tenerlos en todos. Crear cuenta", con una cruz, cuando ya hay al menos un libro. Es un solo aviso por dispositivo: la cruz lo esconde en las dos pantallas por unos días (L2; 7 por defecto, GST-026) y después vuelve, mientras siga sin cuenta. Tocar "Crear cuenta" no lo esconde: si se vuelve sin crear la cuenta, sigue ahí. Desaparece del todo al crear la cuenta o iniciar sesión. Cambiado el 2026-10-16 a pedido del usuario: antes la cruz y "Crear cuenta" lo descartaban para siempre. En la Biblioteca tiene el aspecto del aviso de PLN-011; en Inicio es el banner de GST-023. "Crear cuenta" abre el alta corta (GST-011).
- **GST-023** Banner de Inicio sin cuenta: tarjeta beige con tres portadas inclinadas de ilustración y una insignia amarilla, una cruz arriba a la derecha y "Crear cuenta" con flecha. Toda la tarjeta, salvo la cruz, abre el alta corta. Hay dos opciones:
  - **A** (H0b, "6e", la de siempre): insignia de sincronizar, "Tus libros, en todos tus dispositivos" y los íconos de teléfono, tablet y computadora con "gratis".
  - **B** (H0, "6b"): insignia de subir a la nube, "Tus libros sin respaldo" y "Si cambiás de celu, se pierden. Guardalos gratis."
- **GST-025** La opción del banner se elige desde la administración del producto, sin publicar una versión nueva (como ACC-011). Si la app todavía no lo sabe, vale A. Sirve para probar cuál lleva a más gente a crear la cuenta.
- **GST-026** Los días que la cruz esconde el aviso (GST-010) se cambian desde la administración del producto, sin publicar una versión nueva (como GST-025), para ajustarlos o probar distintos valores. Si la app todavía no lo sabe, valen 7. Un valor negativo vale 0 (vuelve en la próxima apertura). Pedido del usuario (2026-10-16).
- **GST-011** Alta corta, desde Perfil o desde el aviso (variante B): B1 es crear cuenta (O2) con una cruz arriba a la izquierda en lugar de la flecha y "Paso 1 de 2"; después, si hace falta, verificar el email (O3, con el enlace) con "Paso 2 de 2"; y al final B3. Sin meta diaria ni recordatorio. La cruz de B1 vuelve a donde estaba. Con Google se pasa de B1 directo a B3.
- **GST-021** "Ya estás adentro" corto (B3): fondo amarillo, "Ya estás / adentro, {nombre}.", "Tus {N} libros, con su progreso y sus notas, pasan a tu cuenta. Iniciá sesión en otro dispositivo para tenerlos ahí." (sin libros: "Iniciá sesión en otro dispositivo para tener ahí tus libros.") y "Volver a la biblioteca", que abre la Biblioteca. Atrás desde ahí no vuelve al alta.
- **GST-022** "Ya tengo una cuenta" abre el ingreso (O7) de siempre. Al iniciar sesión se abre la Biblioteca; si la cuenta tiene el email sin verificar, verificar (O3, ONB-016).

### Al crear la cuenta o iniciar sesión

- **GST-012** Al crear la cuenta o iniciar sesión, todo lo que estaba en este dispositivo pasa a la cuenta: libros, portadas, posición de lectura y notas. Se suben en la siguiente sincronización (SYN-001), como libros importados sin conexión. Nunca se borra nada del dispositivo.
- **GST-013** Si la cuenta ya tenía libros en la nube, la biblioteca queda con la unión de los dos: un libro que está en los dos lados (el mismo archivo) aparece una sola vez; para su posición gana la lectura más reciente (SYN-003) y sus notas se unen (SYN-004, SYN-005).
- **GST-014** Los libros que no entran en el espacio de la cuenta o superan su máximo por archivo quedan en este dispositivo sin subir, igual que hoy (PLN-009, PLN-014). La línea de estado de la Biblioteca los cuenta. No se muestra una hoja por cada uno al iniciar sesión.
- **GST-015** Los ajustes de lectura y la apariencia de este dispositivo se mantienen al crear la cuenta o iniciar sesión.

### Cerrar sesión

- **GST-016** Cerrar sesión sigue como PRF-009: confirma, avisa lo que no se subió, borra de este dispositivo los datos de la cuenta y vuelve a la bienvenida (GST-001). Desde ahí se puede seguir sin cuenta con la biblioteca vacía. Lo que se usó sin cuenta antes de entrar ya es de la cuenta (GST-012) y también se borra del dispositivo, pero sigue en la nube si se subió.

### Ayuda y accesibilidad

- **GST-017** La respuesta "¿Dónde están mis libros?" (PRF-018) explica también el uso sin cuenta: los libros están solo en este dispositivo, se pierden si se desinstala la app o se cambia de teléfono, y con una cuenta se guardan en la nube. La de "¿Cómo quito un libro?" dice que sin cuenta solo se borra de este dispositivo.
- **GST-018** Lo nuevo es compatible con TalkBack y VoiceOver: la tarjeta de Perfil se anuncia como "Sin cuenta. Crear una cuenta", el aviso con su texto y el botón para cerrarlo, y "Listo" con su título y sus dos botones.

## Escenarios

- Dado que instalo la app, cuando toco "Empezar a leer", entonces veo "Ya podés leer."; cuando toco "Importar mis libros", veo Inicio y el selector de EPUB, sin haber creado una cuenta.
- Dado que entré sin cuenta, cuando cierro la app y la vuelvo a abrir, entonces voy directo a Inicio, sin bienvenida.
- Dado que uso la app sin cuenta, cuando abro la Biblioteca, entonces no veo ningún estado de sincronización ni aviso de cuota, aunque mis libros pesen más que el espacio de Free.
- Dado que importo mi primer libro sin cuenta, cuando vuelvo a Inicio, entonces veo el banner "Tus libros, en todos tus dispositivos"; si lo cierro, no aparece ni en Inicio ni en la Biblioteca por 7 días, y después vuelve.
- Dado que toco "Crear cuenta" en el banner, cuando vuelvo sin crear la cuenta, entonces el banner sigue en Inicio.
- Dado que la administración eligió la opción B, cuando abro Inicio sin cuenta y con libros, entonces el banner dice "Tus libros sin respaldo".
- Dado que uso la app sin cuenta, cuando toco Sepia en el lector, entonces se abre la hoja "Leé con tus temas y fuentes" y el tema no cambia.
- Dado que tengo 3 libros y notas sin cuenta, cuando creo una cuenta desde Perfil, entonces veo "Paso 1 de 2", después "Ya estás adentro" con "Tus 3 libros…", y en otro dispositivo con la misma cuenta aparecen después de sincronizar.
- Dado que tengo sin cuenta un libro que mi cuenta ya tenía en la nube, cuando inicio sesión, entonces lo veo una sola vez, abierto en la lectura más reciente.
- Dado que quité sin cuenta un libro que mi cuenta tiene en la nube, cuando inicio sesión, entonces el libro sigue en la nube y vuelve a mi biblioteca.
- Dado que tengo sin cuenta 40 MB de libros y la cuenta es Free (15 MB), cuando inicio sesión, entonces se suben los que entran, el resto queda solo en este dispositivo y la Biblioteca me dice cuántos no entran.
- Dado que creo la cuenta desde la bienvenida, entonces el alta tiene tres pasos (crear cuenta, verificar, meta diaria), sin intereses.
- Dado que tengo sesión, cuando cierro sesión, entonces veo la bienvenida; y si toco "Empezar a leer", entro con la biblioteca vacía.
- Dado que actualizo la app teniendo sesión, cuando la abro, entonces entro a Inicio con mi cuenta, como antes.

## Fuera de alcance

Cuentas anónimas o provisorias en la nube; sincronizar sin cuenta; copiar o exportar la biblioteca local a otro dispositivo sin cuenta; avisos al desinstalar; meta diaria y recordatorio sin cuenta (siguen en el alta completa, ONB-010); verificación con código de 6 dígitos (B2); libros de regalo en L0; "Iniciar sesión con Apple" en Android; iPhone (plan propio) y Mac; el cobro (`12-plans.md`).
