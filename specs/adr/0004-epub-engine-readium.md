# ADR 0004 — Motor de EPUB: Readium

**Estado:** provisional, revisable antes de construir el lector.

## Contexto

Renderizar EPUB (paginación, ubicaciones, selección de texto, subrayados) es complejo y no es el diferenciador del producto.

## Decisión

Usar los toolkits de Readium: Kotlin en Android, Swift en iOS y Mac. La web usa su propio motor compatible.

## Consecuencias

- No se reescribe el renderizado.
- Las ubicaciones de lectura usan el formato de Readium. Los clientes deben producir ubicaciones compatibles entre sí.
- Hay que verificar licencia y estado de mantenimiento antes de construir.
