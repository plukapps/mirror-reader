# Plan — Tienda en la búsqueda, prueba de concepto (Android)

**Objetivo:** desde la pantalla Buscar, encontrar libros en un catálogo, obtenerlos gratis o con una compra simulada y que entren solos a la biblioteca.

**Specs que implementa:** STO-001 a STO-008 (`specs/product/10-store.md`); amplía LIB-015 (filtros "En mi biblioteca" y "Gratis").
**Rama:** `feature/tienda-poc` (worktree `.claude/worktrees/tienda-poc`, desde `master`). **Tarjetas:** K-160 a K-165. Pedido del usuario (2026-10-10).

## Contexto de la decisión

Investigado con el usuario el 2026-10-10:

- **Amazon y Google Play Libros, descartados como fuente.** No tienen API para que otra app compre o descargue. Sus libros llevan DRM propio que solo abren sus apps. Desde enero de 2026 Amazon deja bajar en EPUB solo los libros de autoedición publicados sin DRM. El camino "comprar allá, bajar, importar" tiene demasiada fricción.
- **Catálogo comercial a futuro: Readium LCP.** Es el DRM abierto y estándar (ISO) que usan muchas tiendas y distribuidores. Readium, el motor de la app, lo soporta. Requiere certificar la app con EDRLab y un acuerdo con un distribuidor o una tienda (por ejemplo Readino, que usa OPDS y LCP). Queda en el Backlog (K-166).
- **Cobro real a futuro:** Google Play Billing, o un enlace a pago web donde se permita (Estados Unidos). Queda en el Backlog (K-167).

## Decisiones técnicas (provisionales; ADR si se sigue)

- **Catálogo: Open Library**, `GET https://openlibrary.org/search.json` con `q=` (o `author=` con "Autores"), `limit=20` y `fields=key,title,author_name,cover_i,ebook_access,id_standard_ebooks,id_project_gutenberg`. Con un `User-Agent` que identifica la app, como pide su documentación. Portada: `https://covers.openlibrary.org/b/id/{cover_i}-M.jpg`.
- **De dónde sale el EPUB (STO-005)**, en este orden:
  1. Standard Ebooks: id `autor/titulo` → `https://standardebooks.org/ebooks/{id}/downloads/{id con "/" → "_"}.epub?source=download`. Es la mejor edición.
  2. Project Gutenberg: id `N` → `https://www.gutenberg.org/ebooks/{N}.epub3.images`.
  3. Sin ninguno de los dos: "No disponible en digital". Las ediciones de préstamo de archive.org (`borrowable`) llevan DRM y no se ofrecen; los escaneos públicos de archive.org se descartan por la calidad del OCR.

  Probado a mano el 2026-10-10 con *Frankenstein*: las dos URL devuelven `application/epub+zip`, de unos 0,5 MB.
- **Precio de prueba (STO-005):** uno de cada tres libros con EPUB lleva precio, según el hash estable de la clave de la obra (`/works/OL…W`): US$ 2,99, 4,99 o 7,99. Así el mismo libro siempre tiene el mismo precio.
- **Entrega (STO-003):** se descarga a un temporal en `cacheDir` y se importa con `LibraryRepository.import(file://…)`, el mismo camino de LIB-001 (hash, validación, metadatos y subida a la nube si hay sesión). El temporal se borra siempre.
- **"En tu biblioteca" (STO-008):** se guarda en DataStore la relación clave de la obra → id del libro al importar (también con `AlreadyInLibrary`). Un resultado del catálogo cuyo libro está en la biblioteca queda en su lugar como "En tu biblioteca" (con el progreso) y no se repite en la parte de la biblioteca: así no desaparece si el título del EPUB no coincide con lo buscado (por ejemplo, buscar "orgullo y prejuicio" y obtener *Pride and Prejudice*). Si el libro se borró de la biblioteca, vuelve a ofrecerse.
- **Red:** `HttpURLConnection` y `org.json` (los dos vienen con Android), sin dependencias nuevas en la app. Para los tests JVM del parser se agrega `org.json:json` solo como `testImplementation` (en JVM, `org.json` de Android es un stub).
- **Búsqueda en el catálogo:** espera 400 ms desde la última tecla y al menos 2 caracteres; una consulta nueva cancela la anterior. Las portadas remotas se cargan con un cargador mínimo (`HttpURLConnection` y `BitmapFactory`) con caché en memoria.
- **Capas:** `domain/store` (modelos, `StoreCatalog`, `StoreAcquisitions`, reglas puras), `data/store` (`OpenLibraryCatalog`, `EpubDownloader`, `StoreAcquisitionsImpl`), `di/StoreModule`. La pantalla sigue siendo `SearchScreen`/`SearchViewModel`.

## Tareas

### Tarea 1 (K-160): spec y plan (S)
- [x] `specs/product/10-store.md` (STO-001 a STO-008), LIB-015 ampliado, este plan y las tarjetas.
**Verificación:** el usuario aprueba el plan.

### Tarea 2 (K-161): dominio (S)
- [x] `domain/store/Store.kt`: `CatalogWork`, `StoreBook`, `Acquisition` (`Free(sources)`, `Paid(sources, priceCents)` o `Unavailable`), `epubSources`, `testPriceCents`, `SearchFilter` (`All`, `InLibrary`, `Free`, `Authors`; la búsqueda local sigue usando `SearchScope`), `libraryResults` y `catalogResults` (filtra por "Gratis"; `StoreResult` lleva el libro de la biblioteca si se obtuvo y sigue ahí, y la biblioteca no lo repite). Interfaces `StoreCatalog`, `StoreDelivery` y `StoreAcquisitions`.
- [x] Tests JVM `StoreRulesTest` (9; STO-002, STO-005, STO-006, STO-008). Mutación del precio de prueba detectada.
**Verificación:** `./gradlew :app:testDebugUnitTest`.

### Tarea 3 (K-162): datos (M)
- [x] `OpenLibraryCatalog` (URL, parser y errores como resultado, nunca excepción; los ids solo si la obra es `public`), `EpubDownloader` (descarga a `cacheDir`, tope de 100 MB, importa; `deliverFirst` prueba las fuentes en orden y borra el temporal), `StoreAcquisitionsImpl` (DataStore), `StoreModule`.
- [x] Tests JVM `OpenLibraryTest` (7): URL, parser con una respuesta guardada, préstamo sin fuentes, `deliverFirst`.
**Verificación:** tests JVM pasan, `assembleDebug` compila.

### Tarea 4 (K-163): `SearchViewModel` (M)
- [x] Suma al estado los resultados del catálogo, si la tienda está cargando o no está disponible, y el estado de cada obra (descargando, falló, en la biblioteca). `onGet`, `onBuy`/`onConfirmPurchase`/`onDismissPurchase`, `onStoreBookClick`.
- [x] Tests JVM en `SearchViewModelTest` (14) con catálogo y descarga falsos: espera antes de buscar, consulta corta, filtros, obtener, compra simulada, fallo y reintento, sin conexión, sin repetir lo obtenido. Mutación de la espera detectada. `SearchScope` deja de usarse en la UI: los chips usan `SearchFilter`.
**Verificación:** tests JVM pasan.

### Tarea 5 (K-164): pantalla (M)
- [ ] Chips "Todo", "En mi biblioteca", "Gratis" y "Autores"; sección del catálogo con fila, botón "Obtener"/"Comprar", "Descargando…", "En tu biblioteca", "No disponible en digital"; hoja de compra simulada; aviso de la tienda no disponible; portada remota.
- [ ] Textos en `strings.xml`. `SearchScreenTest` actualizado para compilar con los nuevos parámetros.
**Verificación:** `assembleDebug` y `compileDebugAndroidTestKotlin`.

### Tarea 6 (K-165): cierre (S)
- [ ] `specs/platforms/android.md`: nota de la tienda. Resultado en este plan.
- [ ] Instalar en el teléfono y probar a mano con el usuario: buscar "frankenstein", obtenerlo gratis, comprar uno con precio de prueba, abrirlo, modo avión.
**Verificación:** el usuario lo ve en el teléfono.

## Riesgos

- Open Library a veces tarda varios segundos. La búsqueda local no lo espera (STO-007).
- No todas las obras tienen ids de Standard Ebooks o Gutenberg: muchos libros de dominio público quedan como "No disponible en digital". Es aceptable para la prueba.
- Un id de Standard Ebooks con varios autores puede no seguir la regla del nombre de archivo. Si la descarga falla, se intenta con Gutenberg si hay id.
