# Android — Aviso de fin de lectura

**Objetivo:** detectar dónde termina el cuerpo del libro (antes del índice, notas, etc.) y mostrar un toast al llegar. Sirve para probar la detección en libros reales antes de usarla para marcar libros como terminados.

**Spec que implementa:** `specs/product/02-reader.md`: RDR-012.

## Decisiones

- La detección usa solo la tabla de contenidos (títulos de primer nivel, español e inglés). Los landmarks de EPUB3 quedan para una segunda iteración: Readium no conserva el `epub:type` de cada entrada.
- Sin cambios en progreso ni estado del libro: solo el aviso.
- El aviso es un `Toast` del sistema, disparado por un evento del `ReaderViewModel`.

## Tareas

### Tarea 1: regla pura y aviso (M) — K-043
- [ ] `backMatterStart(toc, readingOrder)` en `domain`: índice del primer recurso del bloque final, o null.
- [ ] `ReaderViewModel` calcula el índice al abrir y emite `ReaderEvent.BodyEnded` una vez.
- [ ] `ReaderScreen` muestra el toast. Texto en `strings.xml`.
**Verificación:** `BackMatterTest` y `ReaderViewModelTest` (JVM); `:app:assembleDebug`; prueba a mano en el teléfono con libros reales.
