# Plan — Posición exacta entre dispositivos

**Objetivo:** que al continuar un libro en otro dispositivo se abra la página que contiene lo que se estaba leyendo, aunque los dos dispositivos paginen distinto. Hoy hay un desfase de alrededor de una página.

**Specs que implementa:** SYN-014 (nuevo), RDR-006. **Rama:** `feature/exact-position`, desde `develop`. **Plataformas:** Android e iOS. **Pedido del usuario:** 2026-10-12.

## Diagnóstico

El locator que se guarda y se envía (`locatorJson`) es el `currentLocator` de Readium tal cual. En la base del SM-S711B:

```json
{"href":"OEBPS/Text/Section0001.xhtml","locations":{"progression":0.5833,"position":9,"totalProgression":0.0255}}
```

No lleva texto ni selector: solo "capítulo X, al 58,33 %" (página 7 de 12 en ese teléfono). Al restaurar, Readium calcula la página como `round(progression × páginas del capítulo)` en el otro dispositivo (verificado en el código fuente: `R2EpubPageFragment.loadLocator` de Kotlin 3.4.0 y `EPUBReflowableSpreadView.go(to:)` de Swift 3.9.0). Con otra pantalla, otra letra u otros márgenes el capítulo tiene otra cantidad de páginas y el texto no se reparte en proporción, así que se cae una página antes o después.

En los dos toolkits, si el locator trae `text.highlight`, la restauración busca ese texto en el capítulo (`readium.scrollToLocator`, con `before` y `after` como contexto) y va a la página que lo contiene. Ese camino es el que se usa.

## Decisión

- En cada cambio de página, la pantalla del lector corre un script propio en el capítulo visible (`evaluateJavascript` / `evaluateJavaScript`, API pública de los dos toolkits) que encuentra **el primer carácter visible** de la página y devuelve una ventana del texto del capítulo (`textContent` del `body`) alrededor de ese punto.
- Una función pura del dominio, `textAnchorAt(window, start)`, arma `before` (32 caracteres), `highlight` (32, desde el primer carácter que no es espacio) y `after` (32). Se agrega como `text` al locator antes de guardarlo. El resto del locator (y `progress`) no cambia.
- Se descartó `firstVisibleElementLocator()` de Readium: devuelve el párrafo entero como `highlight`, y si el párrafo empieza en la página anterior se vuelve a esa página.
- Mientras el texto del ancla actual siga a la vista, se conserva y no se recalcula. Sin esto, el dispositivo que recibe guardaría su propio primer carácter (un poco antes) y al volver al primero se retrocedía una página: en la prueba, la ida y vuelta volvía a la misma página solo en 2 a 7 de cada 16 a 39 casos; con el ancla conservada, en todos.
- Si el script no puede ubicar el texto (maquetación fija, escritura de derecha a izquierda o vertical, página sin texto) devuelve `null` y se guarda el locator como hoy: la restauración usa la proporción. Los locators viejos, sin `text`, siguen funcionando igual.
- El dato viaja dentro de `locatorJson`: no cambia el modelo de Firestore ni las reglas (≤ 8 KiB alcanza con holgura). Sí guarda ~100 caracteres del libro en el documento del usuario; se anota en el ADR 0011.

## Tareas

- **K-160** Spec SYN-014, ADR 0011 y este plan.
- **K-161** Dominio: `textAnchorAt` en Kotlin y en Swift, con tests (JVM y `swift test`).
- **K-162** Android: script del primer carácter visible y locator con `text` en `ReaderScreen`.
- **K-163** iOS: lo mismo en `ReaderNavigator`.
- **K-164** Verificación a mano entre el teléfono y el iPhone (o el simulador) y cierre de docs.

## Verificación a mano

1. En el teléfono, leer unas páginas a mitad de un capítulo y salir del libro.
2. En el iPhone (o simulador con otro tamaño de letra), abrir el mismo libro: la primera línea que se veía en el teléfono tiene que estar en la página que se abre.
3. Al revés, del iPhone al teléfono.
4. Cambiar el tamaño de letra en un dispositivo y repetir.

## Resultado (2026-10-12)

- Script probado en Chrome sin interfaz sobre un capítulo paginado con columnas CSS, como Readium, a 500×828, 800×1280 y 1280×800: el primer carácter visible coincide con un recorrido exhaustivo en el 100 % de las páginas (con guiones blandos, emojis e imágenes). Con otra letra y otros márgenes, el texto cae en la página correcta en el 100 % de los casos; por proporción, como hasta ahora, se erraba una página en 2 a 17 de cada 16 a 35.
- Android: `TextAnchor.kt`, `FirstVisibleText.kt`, `ReaderScreen`. 322 tests JVM pasan.
- iOS: `TextAnchor.swift`, `FirstVisibleText.swift`, `ReaderNavigator`. 154 tests de dominio y 96 en el simulador pasan; compila para Mac.
- Pendiente: verificación a mano entre dispositivos (K-164).
