# ADR 0009 — Tipografías del lector

**Estado:** provisional.

## Contexto

El panel de ajustes (RDR-014) ofrece tres tipografías: una con serifa, una sin serifa y una monoespaciada (RDR-002). Hoy el lector usa la tipografía del libro. El motor (Readium, ADR 0004) aplica `fontFamily` y `lineHeight` solo si se desactivan los estilos del editor (`publisherStyles = false`), y para usar una fuente propia hay que declararla y servirla desde los assets de la app.

## Decisión

- Las tres fuentes viajan dentro de la app (offline-first, ADR 0002), con licencia libre (OFL): **Newsreader** (serifa, la del diseño), **Host Grotesk** (sin serifa, la misma que ya usa la interfaz, K-042) y **JetBrains Mono** (monoespaciada).
- Se sirven al navegador de EPUB con `servedAssets` y las declaraciones de familia de `EpubNavigatorFragment.Configuration`. La API se verifica con `javap` antes de usarla (`source-driven-development`).
- La elegida es una preferencia de lectura más (con el tema y el tamaño), y por tanto entra en RDR-008 cuando haya sincronización.
- Con una fuente elegida se desactivan los estilos del editor para el tipo de letra y el interlineado. Por defecto, Newsreader, como el diseño. Esto cambia el aspecto de los libros ya abiertos: antes se veían con la fuente del libro.
- El interlineado tiene dos valores fijos (normal y amplio), que se ajustan a ojo con libros reales.

## Consecuencias

- La app pesa unos cientos de KB más por las fuentes.
- Se pierde la tipografía original del editor. Si hace falta, una cuarta opción "Original del libro" se agrega después sin cambiar el modelo.
- Alternativas descartadas: usar solo fuentes del sistema (cambian según el dispositivo y no son las del diseño); descargar fuentes bajo demanda (rompe el funcionamiento sin conexión).
