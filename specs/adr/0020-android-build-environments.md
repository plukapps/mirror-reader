# ADR 0020 — Entornos de build en Android (dev, stage, prod)

**Estado:** provisional. Depende del ADR 0005 (stack de Android) y del ADR 0007 (Firebase).

## Contexto

Hoy Android tiene una sola variante: paquete `com.pluk.reader`, un solo `google-services.json` y el host de los enlaces de la cuenta fijo en `build.gradle.kts`. No se puede tener instalada a la vez la versión que se está desarrollando y la que se prueba o se publica, y no hay un lugar para cambiar por entorno el servidor u otras variables. Pedido del usuario (2026-10-16): un entorno de desarrollo, uno de stage y el de producción, con paquetes distintos, empezando por Android. iOS sigue después con su propio ADR o una ampliación de este.

## Decisiones

### Flavors de entorno, separados de debug y release

- Dimensión `environment` con tres `productFlavors`: `dev`, `stage` y `prod`. Se cruzan con los `buildTypes` `debug` y `release` (seis variantes).
- El entorno decide a qué servidor y con qué variables habla la app; el tipo de build decide si es depurable, minificado y cómo se firma. Así puede existir un `stageRelease` firmado para probar antes de publicar, o un `prodDebug` para revisar un problema de producción.
- Variante por defecto para trabajar: `devDebug`. La que se publica: `prodRelease`.
- Descartado: tres `buildTypes` (`debug`, `staging`, `release`). Son menos variantes, pero atan el entorno a lo depurable.

### Paquete y nombre por entorno (AND-007)

| Flavor | `applicationId` | Nombre visible |
|--------|-----------------|----------------|
| `dev` | `com.pluk.reader.dev` | Tuck Dev |
| `stage` | `com.pluk.reader.stage` | Tuck Stage |
| `prod` | `com.pluk.reader` | Tuck |

- `applicationIdSuffix` en `dev` y `stage`; el `namespace` (paquete del código y de `R`/`BuildConfig`) sigue siendo `com.pluk.reader` en todos.
- El nombre visible sale de `src/<flavor>/res/values/strings.xml`, que pisa `app_name` de `main`.
- El ícono de las variantes que no se publican lleva una etiqueta, como Chrome Beta: DEV (`dev`), STAGE (`stage`) y DEBUG (`prodDebug`); `prodRelease` usa el de `main`. Lo genera `code/android/tools/make_env_icons.py` en `src/<variante>/res/drawable/` (vector, texto en trazados de Host Grotesk). Si cambia el ícono de `main`, se vuelve a correr.

### Configuración por entorno en un solo lugar (AND-008)

- Cada flavor declara sus valores en `build.gradle.kts`: `BuildConfig.ENVIRONMENT` (`dev`, `stage`, `prod`) y el host de los enlaces de la cuenta (`authLinkHost`, que va al manifiesto y a `R.string.auth_link_host`, ADR 0014). Una variable nueva por entorno se agrega ahí.
- El dominio no lee `BuildConfig`: recibe un `AppEnvironment` (enum `DEV`, `STAGE`, `PROD`) que provee Hilt desde `di`. La lógica que dependa del entorno se prueba en JVM con el valor que haga falta.
- Perfil muestra el entorno junto a la versión cuando no es producción (por ejemplo `1.0 · dev`), para saber qué build está abierto.

### Firebase: un solo proyecto por ahora

- Los tres paquetes se registran como apps Android en el proyecto actual, `mirror-reading-staging`. Por ahora comparten usuarios, libros y Remote Config: el entorno separa los paquetes, no los datos.
- Un solo `app/google-services.json` con los tres clientes (el plugin elige el del `applicationId` de la variante). Sigue fuera del repo (ADR 0005).
- Cuando exista un proyecto de producción aparte, su archivo va en `app/src/prod/google-services.json`, que tiene prioridad sobre el de `app/` para los builds de `prod`. Es una tarjeta aparte: exige desplegar reglas, Functions y Remote Config en el proyecto nuevo.
- Ingreso con Google (ADR 0014): cada app registrada necesita el SHA-1 de la firma de debug de la máquina.
- Crashlytics (ADR 0018) y Remote Config (ADR 0016) separan los reportes y las condiciones por app registrada, sin cambios de código.
- Fuente: documentación del plugin de Google Services ("Adding the JSON File": ubicación por flavor y tipo de build) y de Android ("Configure build variants").

## Consecuencias

- Los comandos cambian de nombre: `./gradlew :app:testDevDebugUnitTest`, `:app:connectedDevDebugAndroidTest`, `:app:installDevDebug`. AGENTS.md y los comandos de `adb` (`run-as com.pluk.reader.dev`, `pm clear com.pluk.reader.dev`) se actualizan.
- Un clon nuevo necesita bajar el `google-services.json` después de registrar las dos apps nuevas; el anterior, con un solo cliente, no compila `dev` ni `stage`.
- Los datos locales (Room, DataStore, libros) son de cada paquete: pasar de la variante vieja a `devDebug` empieza con la app vacía. Los libros con cuenta vuelven por la sincronización.
- Los enlaces de verificación de email (ONB-018) abren la app que tenga el host; con varias instaladas Android ofrece elegir hasta que exista `assetlinks.json` (K-157), que debe listar los tres paquetes.
