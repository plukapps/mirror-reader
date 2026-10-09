# ADR 0011 — Sincronización de la posición de lectura

**Estado:** provisional. Depende del ADR 0007 (Firebase) y del ADR 0002 (offline primero).

## Contexto

Continuar una lectura en otro dispositivo es una misión central del producto (SYN-002, SYN-003). La posición cambia con cada página, pesa unos cientos de bytes y debe llegar a los otros dispositivos mucho antes que los libros, que se sincronizan con un TTL de 5 minutos (K-083).

El plan es `specs/plans/2026-10-09-position-sync.md`. El modelo de datos está en `specs/platforms/backend.md`.

## Decisiones

### Dónde vive

Un documento por libro en `users/{uid}/positions/{bookId}`, no un campo dentro del documento del libro. Escribir la posición no toca los metadatos del libro (sin conflictos con `updatedAt` ni con las reglas de `books`) y el listener de posiciones no se despierta cuando cambia un título.

### Quién gana: `readAt`, no la hora del servidor

El principio de `backend.md` es que las marcas de tiempo las pone el servidor. Para la posición se desvía a propósito: "gana la más reciente" (SYN-003) significa la lectura más reciente, no la que llegó último. Con la hora del servidor, un dispositivo que estuvo sin conexión un día enviaría una lectura vieja con hora de servidor nueva y pisaría una más nueva (SYN-010: ningún conflicto puede borrar datos del usuario).

- `readAt` es la hora del dispositivo en que se leyó. La regla de Firestore rechaza escribir un `readAt` menor que el guardado y uno más de una hora en el futuro.
- `updatedAt` sí es la hora del servidor, y solo se usa para pedir "lo que cambió desde la última vez".
- Riesgo aceptado: relojes desfasados. Una diferencia de segundos o minutos entre dispositivos no cambia la experiencia; con más de una hora de adelanto las escrituras se rechazan y quedan pendientes.

### Cuándo se envía

Solo al cerrar el libro: al salir del lector o cuando la app pasa a segundo plano. Un envío por sesión de lectura. Lo que no se pudo enviar queda marcado como pendiente en la base local y se reenvía al volver al primer plano, al arrancar o cuando `LibrarySync` termina una pasada.

Descartado por ahora (deuda técnica K-092): enviar también tras 60 s sin cambiar de página, con un tope de 5 minutos si se lee de corrido. Se retoma solo si aparecen problemas reales.

El costo en dinero no pesa (una escritura de Firestore cuesta menos de una milésima de centavo): lo que se cuida son la batería, los datos y la simplicidad.

### Cómo se recibe

1. Una lectura puntual del documento al abrir el libro (`Source.SERVER`, espera máxima de unos 2 s; sin conexión se sigue con lo local).
2. Un listener en vivo mientras la app está a la vista (`onStart` a `onStop` de la actividad), filtrado por `updatedAt` mayor al último visto. Firestore envía solo lo que cambió; el listener se corta al salir, así que no hay trabajo en segundo plano.

Se descartó consultar cada minuto: lee todos los documentos cada vez aunque nada haya cambiado y tarda hasta un minuto en reflejar el cambio.

Con la caché persistente de Firestore desactivada (ADR 0007), cada conexión del listener recarga lo cambiado desde `lastPositionsSeenAt`, que se guarda en DataStore. En el primer arranque se leen todas las posiciones.

### Qué se muestra

El listener aplica las posiciones nuevas en cuanto llegan, con la app a la vista, y Inicio y la biblioteca muestran el porcentaje actualizado (SYN-012). Por eso, al abrir un libro, la posición suele estar ya aplicada y la pregunta de SYN-003 casi no aparece: la consulta puntual y la pregunta son una red de seguridad para la app recién abierta, el listener sin conexión o la primera apertura en un dispositivo nuevo. Si se quisiera preguntar siempre que el salto sea grande, el listener tendría que guardar la posición remota aparte en lugar de reemplazar la local (más columnas y más lógica); queda como alternativa descartada por ahora.

- Al abrir el libro: diferencia de hasta el 2 % del libro, se salta a la posición más reciente sin preguntar; mayor, se pregunta (SYN-003).
- Mientras se lee: un aviso discreto, sin mover la página, si el otro dispositivo va más adelante por más del 0,5 % del libro (SYN-013). Es un umbral aparte del 2 %: la prueba real mostró que con el 2 % el aviso no aparecía tras leer unas pocas páginas.
- Inicio y la biblioteca ya observan la base local, así que sus porcentajes cambian solos cuando el listener la actualiza (SYN-012).

## Consecuencias

- Solo se ve en vivo el avance de un dispositivo cuando este cierra el libro o pasa a segundo plano. Leer en dos a la vez no se refleja al instante (K-092 lo resolvería).
- La posición pasa a depender del formato de locator de Readium. Apple lo comparte; hay que verificarlo al llevar esto a esa plataforma (K-079).
- Reglas nuevas en Firestore: hay que desplegarlas al proyecto real, acción del usuario.
