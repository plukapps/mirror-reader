# 12 — Planes

## Objetivo

Mostrar qué ofrece cada plan y avisar a tiempo cuando el plan gratuito no alcanza, sin bloquear nunca la lectura ni borrar nada. Es la base visual del modelo de suscripción (open-questions #1): esta rebanada no cobra.

Diseño: `design/Margin Planes.dc.html` (P01 a P07, borrador 2026-10-13). Se construye fiel al diseño, con estas diferencias decididas con el usuario (2026-10-13):

- **La cuota sigue en bytes** (ACC-002, ADR 0008), no en libros. Donde el diseño dice "10 libros" va el espacio ("15 MB").
- **Tamaño máximo por archivo según el plan** (PLN-014). El servidor lo hace cumplir con el catálogo de planes (ACC-010, ADR 0017); hasta que las apps lean el catálogo (K-286, K-287) usan los mismos valores fijos.
- **Sin prueba gratis por ahora.** El botón de compra siempre dice "Elegir…".
- **Solo se muestran las funciones hechas o en camino** (PLN-016): espacio, tamaño por archivo, lectura y sincronización, temas y fuentes, exportar notas. Estadísticas, importar desde Drive, Calibre o email, lector web y familia están en definición y no aparecen.
- **Temas y fuentes son de Plus** (PLN-015): en Free el lector usa el tema Clásico y la letra con serifa.
- **Sin compras.** Los precios son de ejemplo y fijos en la app; desde ACC-007 también están en el catálogo de planes (Remote Config, ADR 0017), de donde las apps los van a leer (K-286, K-287). "Elegir…" y "Restaurar compras" muestran el aviso "Llega más adelante." (PRF-003). La compra con la tienda, la confirmación (P05), el plan vencido (P06) y los estados de la tienda (P07) llegan en su propia rebanada.

Primera rebanada: Android (teléfono y tablet) e iPhone. La Mac queda afuera hasta que tenga su barra lateral (K-078).

## Requisitos

### Catálogo

- **PLN-001** Hay tres planes: Free, Plus y Pro, con los mismos ids en el servidor y en el catálogo (`free`, `plus` y `pro`, ACC-007). Antes se llamaban Gratis, Lector y Biblioteca; el cambio de nombres es decisión del usuario (2026-10-14). Cada uno tiene nombre, bajada, espacio en la nube, tamaño máximo por archivo, lista de lo que incluye y precio mensual y anual. Free usa la cuota real de la cuenta (ACC-002) si la cuenta está en Free; en una cuenta paga muestra la de Free (15 MB) y no la de la cuenta (K-291). El espacio de Plus y Pro lo confirmó el usuario por ahora (2026-10-13); los precios son de ejemplo hasta definirlos (open-questions #1):

  | | Free | Plus | Pro |
  |---|---|---|---|
  | Bajada | Para leer de vez en cuando | Para quien lee siempre | Para quien tiene una biblioteca |
  | Espacio | cuota de la cuenta (hoy 15 MB) | 5 GB | 50 GB |
  | Tamaño máximo por archivo | 2 MB | 50 MB | 100 MB |
  | Mensual | $0 | $2.99 | $5.99 |
  | Anual | $0 | $24.99 ($2.08 por mes) | $49.99 ($4.17 por mes) |

  Lo que incluye cada tarjeta (decisión del usuario, 2026-10-13):

  - Free: "Lectura y notas sin tope", "Sincroniza en todos tus dispositivos", "Archivos de hasta 2 MB".
  - Plus: "Temas y fuentes", "Exportá notas a Markdown, Obsidian y Notion", "Archivos de hasta 50 MB".
  - Pro: "Todo lo de Plus", "Archivos de hasta 100 MB".

  Filas de la tabla comparativa: "Espacio en la nube" (15 MB, 5 GB, 50 GB), "Tamaño por archivo" (2 MB, 50 MB, 100 MB), "Lectura, ajustes y anotaciones" (Sin tope en los tres), "Sincronización en todos tus dispositivos" (✓ en los tres), "Temas y fuentes" (—, ✓, ✓), "Exportar notas (Markdown, Obsidian, Notion)" (—, ✓, ✓).

- **PLN-016** La pantalla de planes, Mi plan y las hojas no muestran funciones en definición: estadísticas y metas, importar desde Drive, Calibre/OPDS o email, lector web y familia. Se agregan cuando estén definidas.

### P01 — Planes

- **PLN-002** La pantalla de planes se abre desde "Mejorar plan" de Perfil (cambia PRF-006), "Cambiar de plan" de Mi plan (PLN-012) y "Ver planes" de los avisos (PLN-007 a PLN-011). Ocupa toda la pantalla, sin barra inferior, y se cierra con la cruz de arriba a la izquierda volviendo a donde estaba.
- **PLN-003** Arriba, el uso de la cuenta: "Usás X de Y" con la misma unidad y formato de PRF-006, la proporción a la derecha en letra monoespaciada y una barra. Sin dato (nunca se leyó la cuota), se oculta la fila. Debajo, el título "Tu biblioteca entera, en todos lados."
- **PLN-004** Un selector Mensual / Anual, con "Ahorrás 30 %" en Anual. Arranca en Mensual (pedido del usuario, 2026-10-13). Cambia los precios de las tarjetas, la equivalencia por mes ("Equivale a $2.08 por mes" en anual, "Pagás mes a mes" en mensual) y el botón de abajo.
- **PLN-005** Una tarjeta por plan, apiladas en teléfono y en tres columnas en tablet (ancho de 600 dp/pt o más). La tarjeta del plan de la cuenta (`free`, `plus` o `pro`, PLN-014) lleva la marca "Tu plan"; sin dato, Free. Plus lleva "Más elegido", salvo que sea el plan de la cuenta: ahí lleva "Tu plan" (PLN-017). Plus y Pro se eligen tocando la tarjeta (radio); arranca elegido Plus. La tarjeta elegida va con fondo amarillo y borde negro; las demás, con el fondo y el borde de las tarjetas comunes (pedido del usuario, 2026-10-13). Free no se elige. Las tres tarjetas tienen la misma estructura y solo cambia el color: arriba el nombre y su marca, la bajada, el espacio, lo que incluye y, debajo de un separador, el precio grande con "por año" o "por mes" y una línea más chica ("Equivale a … por mes" o "Pagás mes a mes"; en Free, "Sin costo, para siempre"). Pedido del usuario (2026-10-13).
- **PLN-006** "Ver todo lo que incluye" despliega y repliega la tabla comparativa (Free, Plus, Pro). Debajo: "Si cancelás, no perdés nada: tus libros y notas quedan." y "Restaurar compras · Términos · Privacidad" (todos avisan, PRF-003). Al pie, fija, la barra de compra: "Elegir {plan} · {precio} por {año|mes}" con "Se renueva cada {año|mes}. Cancelás cuando quieras." No hay prueba gratis por ahora. El botón avisa (PRF-003).

### P02 — Bloqueos de cuota

- **PLN-007** Al importar un libro que no entra en el espacio libre, el libro se importa igual en este dispositivo (se puede leer) y se muestra una hoja inferior. Si el espacio ya está lleno: "Llegaste a tu espacio", "{Título} no se subió a la nube, pero podés leerlo en este dispositivo. Tus otros libros siguen intactos.", la comparación "Free {cuota}" / "Plus 5 GB", "Ver planes" y "Ahora no". Reemplaza la explicación de LIB-009 en el momento de importar.
- **PLN-008** Si queda espacio pero este libro no entra: "Este libro pesa {tamaño}", "Te quedan {libre} en la nube. No se subió, pero podés leerlo en este dispositivo.", la fila del libro con "{tamaño} · te quedan {libre}", "Con Plus: 5 GB en la nube", "Ver planes" y "Ahora no".
- **PLN-014** Cada plan tiene un tamaño máximo por archivo (PLN-001). Al importar un libro más grande que el del plan de la cuenta, el libro se importa igual en este dispositivo y se muestra la hoja "archivo muy grande" del diseño (P02b): la fila del libro con "{tamaño} · límite {máximo}", "Este libro pesa {tamaño}", "Tu plan admite archivos de hasta {máximo}. No se subió a la nube, pero podés leerlo en este dispositivo.", "Con Plus: archivos de hasta 50 MB y 5 GB en la nube" (en Plus: "Con Pro: archivos de hasta 100 MB y 50 GB en la nube"), "Ver planes" y "Ahora no". Esta comprobación va antes que la de espacio. La sincronización no sube los libros que superan el máximo; la línea de estado de la Biblioteca los cuenta con los que no entran ("N libros no entran en tu plan. Podés seguir leyendo."). El plan de la cuenta sale de la cuota (`plan`); si no se conoce o no es uno de los tres, vale Free. Lo controla la app (decisión del usuario, 2026-10-13) y también el servidor, con el máximo de su plan (ACC-010, ADR 0017).
- **PLN-009** La comprobación usa la última cuota conocida (la de PRF-006, que también se actualiza en cada sincronización) y descuenta los libros de este dispositivo que todavía esperan subirse. Si nunca se leyó, no se muestra la hoja y vale el aviso de sincronización de hoy (LIB-009). Si se importan varios libros a la vez, una sola hoja, con el primero que no entra. "Ahora no" cierra la hoja; el libro queda en el dispositivo, sin subir, y se vuelve a intentar en la próxima sincronización.

### P03 — Funciones pagas

- **PLN-010** *Retirado (2026-10-13).* Era la hoja "Importar libros" con Drive y Calibre; esas funciones están en definición (PLN-016). "Importar" vuelve a abrir el selector de archivos directamente.
- **PLN-015** Temas y fuentes son de Plus y Pro. En Free, en el panel del lector (RDR-014) y en "Ajustes de lectura" de Perfil (PRF-013, PRF-014), Clásico y la letra con serifa se eligen como siempre; Sepia, Noche, sin serifa y monoespaciada se ven con una marca "Plus" y, al tocarlos, abren la hoja de función paga (P03): ícono, "Leé con tus temas y fuentes", "Sepia, Noche y todas las letras del lector, en todos tus libros.", "Incluido en Plus", "Ver planes" y "Ahora no". Tamaño e interlineado siguen libres. Si una cuenta Free tenía guardado otro tema o letra, el lector usa Clásico y serifa; la preferencia guardada no se borra y vuelve si la cuenta pasa a un plan pago. Sin dato de plan, vale Free. La hoja de exportar notas (P03a) llega con las anotaciones (ANN).

### Aviso suave

- **PLN-011** Cuando el uso llega al 80 % de la cuota sin llenarla, la Biblioteca muestra arriba de la lista una vez por cuenta el aviso "Usás {usado} de {cuota}" / "Te quedan {libre} en la nube. Ver planes", con una cruz. Cerrarlo o tocar "Ver planes" lo descarta para siempre en esta cuenta y este dispositivo.

### P04 — Mi plan

- **PLN-012** Tocar el bloque "Espacio" de Perfil abre "Mi plan" (sin barra inferior, con "Perfil" para volver): el plan con la marca "Tu plan", "Espacio en la nube {usado} de {cuota}" con barra, "Dispositivos: Todos, sin límite", "Archivos de hasta {máximo}" (el máximo por archivo de la cuenta: el del servidor si se conoce, ACC-008, ACC-010), la tarjeta amarilla "Con Plus: 5 GB en la nube" / "Archivos de hasta 50 MB, temas y fuentes.", "Cambiar de plan" (abre P01) y "Restaurar compras" (avisa, PRF-003). Es la variante Free; la de los planes pagos está en PLN-017.

- **PLN-017** Mi plan en Plus y Pro (diseño "P04 Mi plan · Biblioteca", con lo que existe hoy): igual que PLN-012, pero con el nombre del plan de la cuenta (Plus o Pro) y su máximo por archivo. En Plus (`plus`), la tarjeta amarilla ofrece Pro (`pro`): "Con Pro: {espacio} en la nube" / "Archivos de hasta {máximo}.", con los valores de Pro (los por defecto mientras la app no lea el catálogo, ACC-008). En Pro no hay tarjeta. En Planes (P01), "Tu plan" va en la tarjeta del plan de la cuenta (PLN-005). El plan de la cuenta sale de la cuota como en PLN-014 (sin dato o desconocido, Free). Sin precio pagado, fecha de renovación, "Administrar suscripción" ni Familia: llegan con el cobro. Decisión del usuario (2026-10-14).

### Accesibilidad

- **PLN-013** Compatible con TalkBack y VoiceOver: el selector anuncia la opción elegida, cada tarjeta se anuncia con nombre, espacio, precio y si está elegida, las hojas anuncian su título y la tabla se lee por fila.

## Escenarios

- Dado que uso 12 MB de 15 MB, cuando abro la Biblioteca por primera vez, entonces veo "Usás 12 MB de 15 MB" y "Te quedan 3 MB en la nube"; si lo cierro, no vuelve a aparecer.
- Dado que me quedan 3 MB, cuando importo un libro de 8 MB, entonces el libro aparece en la biblioteca, puedo abrirlo, y veo "Este libro pesa 8 MB" con "Ver planes" y "Ahora no".
- Dado que mi espacio está lleno, cuando importo cualquier libro, entonces veo "Llegaste a tu espacio" y el libro queda solo en este dispositivo.
- Dado que abro Planes, entonces está en Mensual con Plus elegido (en amarillo) y el botón dice "Elegir Plus · $2.99 por mes"; cuando paso a Anual, dice "Elegir Plus · $24.99 por año"; cuando toco Pro, Pro pasa a amarillo y Plus a blanco, y al tocarlo veo "Llega más adelante."
- Dado que estoy en Free, cuando importo un libro de 3 MB, entonces se importa, puedo leerlo, y veo "Este libro pesa 3 MB" con "Tu plan admite archivos de hasta 2 MB".
- Dado que estoy en Free, cuando abro el panel del lector y toco Sepia, entonces el tema no cambia y se abre la hoja "Leé con tus temas y fuentes".
- Dado que estoy en Perfil en el plan gratuito, cuando toco "Mejorar plan", entonces se abre Planes; cuando toco "Espacio", se abre Mi plan.
- Dado que mi cuenta está en Pro (`pro`, 100 MB por archivo), cuando abro Mi plan, entonces veo "Pro" con "Tu plan", "Archivos de hasta 100 MB" y ninguna oferta de otro plan; y en Planes, "Tu plan" está en la tarjeta de Pro y no en la de Free.
- Dado que mi cuenta está en Plus (`plus`, 50 MB por archivo), cuando abro Mi plan, entonces veo "Plus" con "Tu plan", "Archivos de hasta 50 MB" y la tarjeta "Con Pro: 50 GB en la nube".

## Fuera de alcance en esta rebanada

Compras y restauración con Google Play y App Store, prueba gratis real, P05 (compra confirmada), P06 (plan vencido), P07 (estados de la tienda), familia, cambiar la cuota a libros, tope por archivo en el servidor (lo hace ACC-010), P03a (exportar notas), funciones en definición (PLN-016), Mac.
