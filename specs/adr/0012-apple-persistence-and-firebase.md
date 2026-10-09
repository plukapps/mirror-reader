# ADR 0012 — Persistencia local y Firebase en las apps de Apple

**Estado:** provisional. Concreta lo que el ADR 0010 dejó pendiente para Mac e iOS. Sigue al ADR 0002 (offline primero), al 0006 (almacenamiento local de la biblioteca) y al 0007 (Firebase). Plan: `specs/plans/2026-10-09-ios-home-data.md`.

## Contexto

Inicio en Apple mostraba una biblioteca falsa. Para mostrar los libros reales del usuario hace falta lo mismo que Android: una base local que sea la fuente de verdad, y una capa remota que la llene desde Firebase. Android usa Room, DataStore y el SDK de Firebase detrás de interfaces del dominio (ADR 0005).

## Decisión

**Base local: SwiftData**
- Es el equivalente de Room que trae el sistema desde macOS 14 e iOS 17 (nuestras versiones mínimas). Sin dependencias externas.
- Un modelo `BookRecord` con los mismos campos que `BookEntity` de Android que hoy hacen falta (id, título, autor, portada, fecha de alta, tamaño, descargado, subido), y `PositionRecord` como `ReadingPositionEntity` (locator, progreso, momento de lectura). Los demás se agregan con su rebanada.
- El acceso pasa por un `@ModelActor` (`LibraryStore`), que implementa los protocolos del dominio. Así el resto de la app no ve SwiftData y la concurrencia estricta de Swift 6 queda conforme.
- Archivos (portadas, y después los EPUB) en `Application Support`, con las mismas rutas que Android: `covers/{hash}.jpg`.
- Preferencias (equivalente de DataStore): cuando haga falta, `UserDefaults`. No se usa en esta rebanada.

**Remoto: SDK de Firebase para Apple (Swift Package Manager)**
- `firebase-ios-sdk`, productos `FirebaseAuth`, `FirebaseFirestore` y `FirebaseStorage`, declarados en `project.yml`.
- Igual que Android: la base local manda y el caché en disco de Firestore se desactiva (caché en memoria).
- Las implementaciones (`FirestoreRemoteLibrary`, `FirebaseCoverStore`, `FirebaseAccountRepository`) viven en `App/Data/Remote` y el dominio solo ve protocolos.
- `GoogleService-Info.plist` se descarga de la consola (app de iOS `com.pluk.reader` del proyecto `mirror-reading-staging`) y **no va al repo**, como `google-services.json`. Sin él la app compila y funciona solo en local.
- En la Mac el sandbox necesita el permiso de cliente de red (`com.apple.security.network.client`).

**Sesión: cuenta de desarrollo, como Android**
- Mientras no existan las pantallas de cuenta, la app inicia sesión con email y contraseña de una cuenta de desarrollo, solo en builds Debug.
- Las credenciales van en `Signing.local.xcconfig` (fuera del repo) y llegan a la app por el `Info.plist`. En Release quedan vacías. Sin credenciales, la app funciona solo en local (ADR 0002).

## Alternativas consideradas

- **GRDB (SQLite).** Más control y madurez que SwiftData, pero es una dependencia externa y más código para un modelo chico. Se reconsidera si SwiftData falla con la migración o el rendimiento.
- **Core Data.** Estable pero más verboso; SwiftData es su capa moderna.
- **Caché en disco de Firestore como base local.** Descartado por el ADR 0002 y por coherencia con Android: la fuente de verdad es nuestra base.
- **API REST de Firebase sin SDK.** Evita la dependencia pero obliga a reimplementar autenticación, reintentos y tokens.

## Consecuencias

- El primer build descarga el SDK de Firebase y sus dependencias (varios minutos).
- Un cambio de modelo de SwiftData que no sea aditivo necesita un plan de migración (`VersionedSchema`).
- En la Mac, Firebase Auth guarda la sesión en el llavero, y con firma local falla (`SecItemAdd` -34018, falta el permiso). Por eso la Mac se firma con el equipo de desarrollo (el gratuito alcanza, el de `Signing.local.xcconfig`) y declara `keychain-access-groups`. La Mac ya no corre sin equipo configurado.
