# 12 — Planes

## Objetivo

Mostrar qué ofrece cada plan y avisar a tiempo cuando el plan gratuito no alcanza, sin bloquear nunca la lectura ni borrar nada. Es la base visual del modelo de suscripción (open-questions #1): esta rebanada no cobra.

Diseño: `design/Margin Planes.dc.html` (P01 a P07, borrador 2026-10-13). Se construye fiel al diseño, con dos diferencias decididas con el usuario (2026-10-13):

- **La cuota sigue en bytes** (ACC-002, ADR 0008), no en libros. Donde el diseño dice "10 libros" va el espacio ("15 MB"), y el aviso de "archivo muy grande" pasa a ser "este libro no entra en el espacio que te queda". No hay tope por archivo además del de 100 MB del servidor.
- **Sin compras.** Los precios son de ejemplo y fijos en la app. "Probar…", "Elegir…" y "Restaurar compras" muestran el aviso "Llega más adelante." (PRF-003). La compra con la tienda, la confirmación (P05), el plan vencido (P06) y los estados de la tienda (P07) llegan en su propia rebanada.

Primera rebanada: Android (teléfono y tablet) e iPhone. La Mac queda afuera hasta que tenga su barra lateral (K-078).

## Requisitos

### Catálogo

- **PLN-001** Hay tres planes: Gratis, Lector y Biblioteca. Cada uno tiene nombre, bajada, espacio en la nube, lista de lo que incluye y precio mensual y anual. Gratis usa la cuota real de la cuenta (ACC-002). Lector y Biblioteca usan valores provisionales hasta definir precios (open-questions #1):

  | | Gratis | Lector | Biblioteca |
  |---|---|---|---|
  | Bajada | Para leer de vez en cuando | Para quien lee siempre | Para quien tiene una biblioteca |
  | Espacio | cuota de la cuenta (hoy 15 MB) | 5 GB | 50 GB |
  | Mensual | $0 | $2.99 | $5.99 |
  | Anual | $0 | $24.99 ($2.08 por mes) | $49.99 ($4.17 por mes) |

  Lo que incluye cada tarjeta y las filas de la tabla comparativa son las del diseño, salvo "Libros en la nube" (pasa a "Espacio en la nube") y "Tamaño por archivo" y "Archivos de hasta…" (se quitan). Las funciones que todavía no existen (exportar, estadísticas avanzadas, temas propios, Drive, familia) se listan igual: describen el plan, no la app de hoy.

### P01 — Planes

- **PLN-002** La pantalla de planes se abre desde "Mejorar plan" de Perfil (cambia PRF-006), "Cambiar de plan" de Mi plan (PLN-012) y "Ver planes" de los avisos (PLN-007 a PLN-011). Ocupa toda la pantalla, sin barra inferior, y se cierra con la cruz de arriba a la izquierda volviendo a donde estaba.
- **PLN-003** Arriba, el uso de la cuenta: "Usás X de Y" con la misma unidad y formato de PRF-006, la proporción a la derecha en letra monoespaciada y una barra. Sin dato (nunca se leyó la cuota), se oculta la fila. Debajo, el título "Tu biblioteca entera, en todos lados."
- **PLN-004** Un selector Mensual / Anual, con "Ahorrás 30 %" en Anual. Arranca en Anual. Cambia los precios de las tarjetas, la equivalencia por mes ("Equivale a $2.08 por mes" en anual, "Pagás mes a mes" en mensual) y el botón de abajo.
- **PLN-005** Una tarjeta por plan, apiladas en teléfono y en tres columnas en tablet (ancho de 600 dp/pt o más). Gratis lleva la marca "Tu plan" si es el plan de la cuenta. Lector va en amarillo con "Más elegido". Lector y Biblioteca se eligen tocando la tarjeta (radio); arranca elegido Lector. Gratis no se elige.
- **PLN-006** "Ver todo lo que incluye" despliega y repliega la tabla comparativa (Gratis, Lector, Biblio.). Debajo: "Si cancelás, no perdés nada: tus libros y notas quedan." y "Restaurar compras · Términos · Privacidad" (todos avisan, PRF-003). Al pie, fija, la barra de compra: en anual "Probar {plan} 14 días gratis" con "Después {precio} por año. Cancelás cuando quieras."; en mensual "Elegir {plan} · {precio} por mes" con "Se renueva cada mes. Cancelás cuando quieras." El botón avisa (PRF-003).

### P02 — Bloqueos de cuota

- **PLN-007** Al importar un libro que no entra en el espacio libre, el libro se importa igual en este dispositivo (se puede leer) y se muestra una hoja inferior. Si el espacio ya está lleno: "Llegaste a tu espacio", "{Título} no se subió a la nube, pero podés leerlo en este dispositivo. Tus otros libros siguen intactos.", la comparación "Gratis {cuota}" / "Lector 5 GB", "Ver planes" y "Ahora no". Reemplaza la explicación de LIB-009 en el momento de importar.
- **PLN-008** Si queda espacio pero este libro no entra: "Este libro pesa {tamaño}", "Te quedan {libre} en la nube. No se subió, pero podés leerlo en este dispositivo.", la fila del libro con "{tamaño} · te quedan {libre}", "Con Lector: 5 GB en la nube", "Ver planes" y "Ahora no".
- **PLN-009** La comprobación usa la última cuota conocida (la de PRF-006, que también se actualiza en cada sincronización) y descuenta los libros de este dispositivo que todavía esperan subirse. Si nunca se leyó, no se muestra la hoja y vale el aviso de sincronización de hoy (LIB-009). Si se importan varios libros a la vez, una sola hoja, con el primero que no entra. "Ahora no" cierra la hoja; el libro queda en el dispositivo, sin subir, y se vuelve a intentar en la próxima sincronización.

### P03 — Funciones pagas

- **PLN-010** "Importar" abre la hoja "Importar libros" con "Desde este dispositivo" (hace lo de hoy), "Google Drive" y "Calibre / OPDS", estas dos con la marca "Biblioteca". Tocar una de las dos abre la hoja de función paga: ícono, "Traé tus libros desde Drive" (o "…desde Calibre"), la bajada del diseño, "Incluido en Biblioteca", "Ver planes" y "Ahora no". La hoja de exportar notas (P03a) llega con las anotaciones (ANN).

### Aviso suave

- **PLN-011** Cuando el uso llega al 80 % de la cuota sin llenarla, la Biblioteca muestra arriba de la lista una vez por cuenta el aviso "Usás {usado} de {cuota}" / "Te quedan {libre} en la nube. Ver planes", con una cruz. Cerrarlo o tocar "Ver planes" lo descarta para siempre en esta cuenta y este dispositivo.

### P04 — Mi plan

- **PLN-012** Tocar el bloque "Espacio" de Perfil abre "Mi plan" (sin barra inferior, con "Perfil" para volver): el plan con la marca "Tu plan", "Espacio en la nube {usado} de {cuota}" con barra, "Dispositivos: Todos, sin límite", la tarjeta amarilla "Con Lector: 5 GB en la nube" / "Y exportás tus notas a Obsidian o Notion.", "Cambiar de plan" (abre P01) y "Restaurar compras" (avisa, PRF-003). En esta rebanada solo existe la variante Gratis.

### Accesibilidad

- **PLN-013** Compatible con TalkBack y VoiceOver: el selector anuncia la opción elegida, cada tarjeta se anuncia con nombre, espacio, precio y si está elegida, las hojas anuncian su título y la tabla se lee por fila.

## Escenarios

- Dado que uso 12 MB de 15 MB, cuando abro la Biblioteca por primera vez, entonces veo "Usás 12 MB de 15 MB" y "Te quedan 3 MB en la nube"; si lo cierro, no vuelve a aparecer.
- Dado que me quedan 3 MB, cuando importo un libro de 8 MB, entonces el libro aparece en la biblioteca, puedo abrirlo, y veo "Este libro pesa 8 MB" con "Ver planes" y "Ahora no".
- Dado que mi espacio está lleno, cuando importo cualquier libro, entonces veo "Llegaste a tu espacio" y el libro queda solo en este dispositivo.
- Dado que abro Planes en anual con Lector elegido, cuando paso a Mensual, entonces el botón dice "Elegir Lector · $2.99 por mes", y al tocarlo veo "Llega más adelante."
- Dado que estoy en Perfil en el plan gratuito, cuando toco "Mejorar plan", entonces se abre Planes; cuando toco "Espacio", se abre Mi plan.

## Fuera de alcance en esta rebanada

Compras y restauración con Google Play y App Store, prueba gratis real, P05 (compra confirmada), P06 (plan vencido), P07 (estados de la tienda), Mi plan de planes pagos y familia, cambiar la cuota a libros, P03a (exportar notas), Mac.
