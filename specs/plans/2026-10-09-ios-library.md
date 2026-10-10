# Plan — Biblioteca en Apple (iPhone y Mac)

**Objetivo:** la pantalla de biblioteca de Android (K-029 a K-033, plan `2026-10-06-android-library.md`) en Apple, con los libros que ya trae la base local (Inicio con datos reales, plan `2026-10-09-ios-home-data.md`), según "05 — Library" del diseño.

**Specs que implementa:** LIB-007 (marca "En la nube"), LIB-010, LIB-011, HOM-005, HOM-011 (`specs/product/01-library.md`, `07-home.md`), IOS-001, MAC-001.
**Rama:** `feature/ios-library`. **Tarjetas:** K-119 a K-122. Pedido del usuario (2026-10-09), sin confirmación previa.

## Diseño (igual que Android)

- Encabezado "Biblioteca" (44 pt, tracking −0,045 em) y botón amarillo "+ Importar" (40 pt de alto, cápsula).
- Pestañas "Todos N", "Leyendo N", "Terminados N" (14 pt seminegrita) sobre una línea `#DDD9CE`; la activa en tinta con barra de 3 pt, las otras en gris.
- Grilla adaptable (mínimo 96 pt por columna, 3 columnas en 360 pt), 12 pt entre columnas y 16 pt entre filas. Cada celda: portada 2:3 y debajo el estado: barra de progreso de 3 pt y "N %" (leyendo), "✓ Terminado" o "Nuevo". Los libros solo en la nube llevan "En la nube" (LIB-007).
- La pestaña "Notes" del diseño queda fuera (ANN, sin implementar), como en Android.

## Decisiones

- **Sin importar todavía.** Importar en Apple exige descomprimir el EPUB y leer sus metadatos (motor o librería de ZIP, ADR propio). Queda en K-079. El botón "Importar" se ve como en el diseño y muestra el aviso "Llega más adelante." (HOM-006) en iPhone; en la Mac, que no tiene dónde mostrar el aviso hasta K-078, no se muestra.
- **Sin estado de sincronización (SYN-008).** En Apple la sincronización solo baja de la nube y corre una vez al arrancar; no hay subida ni reintento. La biblioteca se recarga cuando la sincronización cambia la base.
- **Tocar un libro no hace nada:** Apple todavía no tiene lector.
- **Navegación.** iPhone: Estantes abre la biblioteca; "Ver todo" de Inicio pasa a Estantes con el filtro de la sección (HOM-011: Leyendo → Leyendo, Agregados recientemente → Todos, Terminados → Terminados) e "Ir a la biblioteca" con Todos. Mac (sin barra, K-078): "Ver todo" e "Ir a la biblioteca" empujan la biblioteca en una `NavigationStack`, con volver.
- **Dominio:** `LibraryFilter`, `filter(by:)` y `count(by:)` en `ReaderDomain`, como Android. `LibraryBook` suma `isDownloaded` (por defecto verdadero).

## Tareas

### Tarea 1: dominio (S) — K-119
- [x] `LibraryFilter`, `filter(by:)`, `count(by:)`; `LibraryBook.isDownloaded`.
- [x] Tests con `swift test` (LIB-010).

### Tarea 2: datos y ViewModel (S) — K-120
- [x] `LibraryStore.books()` entrega `isDownloaded`. Test.
- [x] `LibraryViewModel` (`@MainActor @Observable`): carga, filtro, conteos, filtro inicial elegido desde Inicio, recarga. Tests.

### Tarea 3: pantalla y navegación (M) — K-121
- [x] `LibraryView` según el diseño (encabezado, pestañas, grilla, vacío).
- [x] Estantes disponible; `RootView` cambia entre Inicio y Biblioteca en iPhone y empuja la biblioteca en la Mac; "Ver todo" e "Ir a la biblioteca" navegan.
- [x] `AppGraph` arma los dos ViewModel sobre la misma base; la sincronización recarga los dos.
- [x] Tests del modelo de la barra (Estantes disponible).

### Tarea 4: cierre (S) — K-122
- [x] `specs/platforms/apple.md`, README, Kanban.
- [x] Tests en iPhone, Mac y dominio; verificación a ojo en el simulador.

## Resultado

Tests: 32 de la app en iPhone (iPhone SE), 31 del dominio, todos pasan. La Mac compila y abre; sus tests no arrancan por un problema de firma que ya existe en `master` (K-123).

Verificado en el simulador (iPhone SE, 375 pt, IOS-001) contra el proyecto real, arrancando temporalmente en Estantes (cambio revertido): 25 libros con sus portadas, "Todos 25 · Leyendo 8 · Terminados 1", "En la nube" y la barra de progreso; la grilla pasa bajo la barra. Inicio se sigue viendo igual.

Dos problemas que encontró la prueba y se corrigieron:

- La sincronización arrancaba desde la tarea de Inicio: si la app abría en otra pantalla no sincronizaba, y cambiar de pestaña a mitad cancelaba la tarea. Ahora arranca desde `RootView`; Inicio y la biblioteca solo releen la base al aparecer (`HomeViewModel.reload()`, test nuevo).
- Las portadas que no son 2:3 desbordaban la columna de la grilla. `BookCover` fija el marco 2:3 y recorta la imagen.

Sin probar a mano: tocar las pestañas, "Ver todo", Estantes y el aviso de "Importar" (no hay forma de tocar el simulador desde aquí), y la biblioteca en la Mac. Ojo: otra sesión usaba el simulador "iPhone 16" con la misma app; para no pisarse se usó el iPhone SE.
