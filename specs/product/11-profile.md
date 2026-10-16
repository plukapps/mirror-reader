# 11 — Perfil

## Objetivo

Un solo lugar para la cuenta, el espacio, los ajustes y la ayuda, al que se llega desde el último destino de la barra inferior ("Perfil", HOM-005). Es una pantalla utilitaria: se entra poco, cuando hace falta algo.

Diseño: "09 — Profile" (sección 4a) de `design/Margin Ebook App.dc.html`. La pantalla se construye completa y fiel al diseño desde la primera rebanada; las funciones que todavía no existen se ven igual pero, al tocarlas, avisan que llegan más adelante (PRF-003). Cada una se completa en su propia rebanada.

Primera rebanada: Android (teléfono y tablet) e iPhone. La Mac queda afuera hasta que tenga su barra lateral (K-078).

## Requisitos

### Pantalla

- **PRF-001** Tocar "Perfil" en la barra inferior abre la pantalla de Perfil y marca ese destino, como los demás (HOM-005). Reemplaza a HOM-006 para Perfil.
- **PRF-002** La pantalla sigue el diseño 09 en medidas, colores, tipografía, radios, separaciones e íconos. Textos en español; tipografía de interfaz de la app (ADR 0009) en lugar de la del diseño. Se desplaza entera y su contenido termina por encima de la barra inferior flotante, sin quedar tapado. En tablet el contenido se centra con un ancho máximo.
- **PRF-003** Las filas y controles cuya función todavía no existe se muestran como en el diseño y, al tocarlos, muestran el aviso breve "Llega más adelante." (el mismo de HOM-006). No cambian de estado ni navegan.
- **PRF-004** Compatible con TalkBack y VoiceOver: cada fila se anuncia con su texto y, si lo tiene, su valor o estado (por ejemplo, "Sincronizar solo con Wi-Fi, desactivado"); los títulos de sección se anuncian como encabezados.

### Cabecera

- **PRF-005** Arriba, una tarjeta amarilla con un círculo con la inicial del nombre, el nombre y el email de la cuenta (el email se corta con "…" si no entra) y una flecha. Si la cuenta no tiene nombre, el círculo muestra la inicial del email y en lugar del nombre va el email solo. Tocarla: aviso (PRF-003); la pantalla de cuenta llega más adelante.

### Cuenta

- **PRF-006** Sección "Cuenta". Primero el espacio (ACC-003): "Espacio", la etiqueta del plan ("GRATIS"), una barra con la proporción usada, "X GB de Y GB usados" y "Z GB libres". Los tres tamaños usan la unidad del total (GB si el total es de 1 GB o más; si no, MB), con un decimal como máximo y coma decimal ("2 GB", "1,2 GB", "3,4 MB"). "Mejorar plan" aparece solo en el plan gratuito y abre Planes (PLN-002). Tocar el bloque del espacio abre Mi plan (PLN-012). Sin conexión o si no se puede leer, muestra el último valor conocido; si nunca se leyó, la barra vacía y "No se pudo cargar el espacio".
- **PRF-007** "Dispositivos vinculados" (ACC-006): por ahora lista solo este dispositivo, con su ícono (teléfono o tablet), su nombre y "· este dispositivo". La lista de los otros dispositivos y "Desvincular" llegan con el registro de dispositivos. Mientras tanto la sección no se muestra: después del espacio sigue directamente "Cambiar contraseña" (o "Cerrar sesión").
- **PRF-008** "Cambiar contraseña" se muestra solo si la cuenta entra con email y contraseña; con Google (o Apple) no aparece. Tocarla abre la pantalla de PRF-020.
- **PRF-009** "Cerrar sesión" pide confirmación. Si hay cambios de este dispositivo que todavía no se subieron (libros importados sin subir, progreso, libros quitados o notas), la confirmación lo dice y avisa que se pierden. Al confirmar: se cierra la sesión, se borran de este dispositivo los libros, portadas, progreso y preferencias de esta cuenta (siguen en la nube), y la app vuelve a la bienvenida (ONB-001). Al volver a entrar con la misma cuenta, la biblioteca vuelve desde la nube (escenario de ACC).
- **PRF-010** "Eliminar cuenta", debajo de "Cerrar sesión", en rojo (CMP-001). En esta rebanada avisa (PRF-003); el borrado real llega en su propia rebanada, antes de publicar.

### Ajustes de lectura

- **PRF-011** Sección "Ajustes de lectura" con la bajada "Se aplican a todos tus libros." Los ajustes son los mismos del panel del lector (RDR-014) y se guardan en el mismo lugar: cambiar uno aquí cambia el lector y viceversa.
- **PRF-012** Una vista previa con un párrafo de muestra que usa en el momento la letra, el tamaño, el tema y el interlineado elegidos.
- **PRF-013** "Letra" muestra el tipo y el tamaño (por ejemplo "Newsreader · 18", escrito en esa letra). Tocarla abre un panel con el tipo de letra y los botones "A−" y "A+" del panel del lector. En Free, las opciones pagas abren la hoja de función paga (PLN-015).
- **PRF-014** "Tema": tres círculos (Clásico, Sepia, Noche) con el elegido resaltado como en el diseño; tocar uno lo elige. En Free, las opciones pagas abren la hoja de función paga (PLN-015).
- **PRF-015** "Interlineado": botones con ícono para las opciones del lector, el elegido en negro con el ícono amarillo; tocar uno lo elige.

### App

- **PRF-016** Sección "App": "Apariencia" (Claro, Oscuro, Sistema; PRF-021), "Sincronizar solo con Wi-Fi" ("Ahorra datos móviles"; PRF-023) y "Descargar libros automáticamente" ("Libros nuevos de tus otros dispositivos"). "Descargar libros automáticamente" todavía muestra el comportamiento de hoy (apagado) y avisa al tocarlo (PRF-003). La fila "Idioma" no se muestra hasta que haya un segundo idioma.

### Cambiar contraseña

- **PRF-020** "Cambiar contraseña" abre una pantalla con flecha atrás, el título "Cambiar contraseña", y tres campos con ver u ocultar: "Contraseña actual", "Contraseña nueva" (con las reglas de ONB-005 marcadas a medida que se cumplen y su medidor) y "Confirmar contraseña". "Guardar" se habilita con la actual no vacía, la nueva válida (ONB-005), distinta de la actual e igual a la confirmación. Mientras espera al servidor muestra que está trabajando (ONB-021).
  - Si la actual es incorrecta, lo dice en rojo bajo ese campo ("La contraseña actual no es correcta."). Si la nueva es igual a la actual, lo dice bajo la nueva ("Tiene que ser distinta de la actual."). Demasiados intentos y sin conexión se avisan igual que en ONB-015.
  - Al guardar bien, vuelve a Perfil con el aviso "Contraseña actualizada.". La sesión de este dispositivo sigue abierta; los otros dispositivos pueden pedir volver a entrar.
  - Atrás descarta sin cambiar nada.

### Apariencia

- **PRF-021** "Apariencia" elige entre Claro, Oscuro y Sistema (sigue el modo del dispositivo y cambia con él). Por defecto, Sistema. El cambio se aplica en el momento a toda la app salvo el lector (que tiene su propio tema, RDR-003) y el onboarding (O1 a O9, siempre como su diseño). La barra de estado y la de navegación del sistema usan íconos que se leen sobre el fondo de cada modo. La elección es de este dispositivo: no se sincroniza.
- **PRF-022** El modo oscuro usa la paleta oscura derivada del diseño (los colores del panel de ajustes y de la tarjeta de ayuda), con el mismo contraste que el modo claro. La cabecera amarilla, el amarillo de lo elegido y el rojo de lo destructivo se mantienen.

### Sincronizar solo con Wi-Fi

- **PRF-023** Con "Sincronizar solo con Wi-Fi" encendido, este dispositivo no sincroniza nada (biblioteca, archivos, portadas, posición de lectura, libros quitados ni libros nuevos de otros dispositivos) mientras no esté conectado a Wi-Fi o por cable. Todo lo local sigue funcionando y lo pendiente se guarda. Apagado por defecto. La elección es de este dispositivo.
  - Al conectarse a Wi-Fi con la app a la vista, sincroniza solo; al volver a la app con Wi-Fi, también (SYN-001).
  - Mientras espera, la línea de estado de la biblioteca (SYN-008) dice "Esperando Wi-Fi para sincronizar.", también si no hay ninguna red. No es un error: no ofrece reintentar.
  - Abrir un libro que está solo en la nube sin Wi-Fi avisa "Este libro se descarga con Wi-Fi. Podés cambiarlo en Perfil." y no lo baja.
  - Apagarlo sincroniza en el momento si hay conexión.

### Ayuda y pie

- **PRF-017** Sección "Ayuda" en una tarjeta oscura: "Preguntas frecuentes" con tres preguntas ("¿Por qué no abre este EPUB?", "¿Dónde están mis libros?", "¿Cuánto espacio tengo?") y "Todas las preguntas". Tocar una pregunta abre su respuesta en una pantalla con el título de la pregunta, el texto y un botón para volver. "Todas las preguntas" abre la lista completa (PRF-018).
- **PRF-018** Las preguntas frecuentes viven en la app (funcionan sin conexión). Lista inicial: por qué no abre un EPUB (con DRM no se puede, CMP-006; archivo dañado), dónde están mis libros (en la nube y en cada dispositivo; "solo en la nube" se baja al abrir), cuánto espacio tengo (la cuota, qué cuenta y qué no, LIB-012), cómo agrego libros, cómo se sincroniza mi lectura, cómo quito un libro.
- **PRF-019** Al pie: "tuck." con la versión de la app a la izquierda y "Términos · Privacidad" a la derecha. Mientras no estén publicados, "Términos" y "Privacidad" avisan (PRF-003).

## Escenarios

- Dado que toco "Perfil", entonces veo mi nombre y email arriba y "Perfil" marcado en la barra.
- Dado que uso 1,2 GB de 2 GB en el plan gratuito, cuando abro Perfil, entonces veo la barra al 60 %, "1,2 GB de 2 GB usados", "0,8 GB libres" y "Mejorar plan".
- Dado que entré con Google, cuando abro Perfil, entonces no veo "Cambiar contraseña".
- Dado que toco "Descargar libros automáticamente", entonces veo "Llega más adelante." y el interruptor sigue apagado.
- Dado que elijo Sepia en Perfil, cuando abro un libro, entonces se ve en sepia.
- Dado que agrando la letra en el lector, cuando vuelvo a Perfil, entonces la fila "Letra" y la vista previa muestran el tamaño nuevo.
- Dado que hay progreso sin subir, cuando toco "Cerrar sesión", entonces la confirmación avisa que se pierde.
- Dado que cierro sesión, entonces veo la bienvenida; y si entro con otra cuenta, no veo los libros de la anterior.
- Dado que estoy sin conexión, cuando toco "¿Por qué no abre este EPUB?", entonces leo la respuesta.
- Dado que escribo mal la contraseña actual, cuando toco "Guardar", entonces veo "La contraseña actual no es correcta." y la contraseña no cambia.
- Dado que elijo Oscuro, entonces Inicio, Estantes, Buscar y Perfil se ven oscuros y el lector conserva su tema.
- Dado que elijo Sistema y el teléfono pasa a modo oscuro, entonces la app pasa a oscuro sin reiniciarla.
- Dado que activo "Sincronizar solo con Wi-Fi" y estoy con datos móviles, cuando importo un libro, entonces queda en este dispositivo, la biblioteca dice "Esperando Wi-Fi para sincronizar." y se sube al conectarme a Wi-Fi.

## Fuera de alcance en esta rebanada

Pantalla de cuenta (editar nombre), planes pagos (Planes y Mi plan están en `12-plans.md`), registro de dispositivos y "Desvincular", borrado real de la cuenta (CMP-001, rebanada propia antes de publicar), descarga automática, tema oscuro del onboarding, idioma, ajustes de lectura por libro, exportar datos (CMP-008), páginas de Términos y Privacidad (CMP-002, CMP-003), contacto (CMP-004), meta diaria e intereses del onboarding, Mac.
