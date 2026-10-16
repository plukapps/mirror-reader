# ADR 0020 — Entornos de build en Android (dev, stage, prod)

**Estado:** provisional. Depende del ADR 0005 (stack de Android) y del ADR 0007 (Firebase).

## Contexto

Hoy Android tiene una sola variante: paquete `com.pluk.reader`, un solo `google-services.json` y el host de los enlaces de la cuenta fijo en `build.gradle.kts`. No se puede tener instalada a la vez la versión que se está desarrollando y la que se prueba o se publica, y no hay un lugar para cambiar por entorno el servidor u otras variables. Pedido del usuario (2026-10-16): un entorno de desarrollo, uno de stage y el de producción, con paquetes distintos, empezando por Android. iOS sigue después con su propio ADR o una ampliación de este.

## Decisiones

### Flavors de entorno, cada uno con su tipo de build

- Dimensión `environment` con cuatro `productFlavors`: `dev`, `stage`, `beta` y `prod`. Cada uno se construye con un solo tipo de build (pedido del usuario, 2026-10-16):

  | Entorno | Tipo de build | Variante |
  |---------|---------------|----------|
  | `dev` | `debug` | `devDebug` |
  | `stage` | `debug` | `stageDebug` |
  | `beta` | `release` | `betaRelease` |
  | `prod` | `release` | `prodRelease` |

- El mapa `ENVIRONMENT_BUILD_TYPE` de `app/build.gradle.kts` lo fija y `androidComponents.beforeVariants` apaga las demás combinaciones. Habilitar otra (por ejemplo `prodDebug` para revisar un problema de producción) es cambiar ese filtro.
- El entorno decide a qué servidor y con qué variables habla la app; el tipo de build, si es depurable, minificado y cómo se firma. Se mantienen separados aunque hoy cada entorno use uno solo.
- Variante por defecto para trabajar: `devDebug`. Beta es la prueba de un build de release antes de publicar; se publica `prodRelease`.
- Beta se firma por ahora con la clave de debug (no hay clave de release): se puede instalar y Google funciona con el SHA-1 ya registrado. Cuando exista la clave de release (con la publicación, K-011), beta y prod pasan a usarla y su SHA-1 se registra en Firebase.
- AGP 9 arma tests JVM solo para `debug`: corren en `devDebug` y `stageDebug`. En beta y prod se revisa el APK (paquete, nombre, no depurable, firma).
- Descartado: tres `buildTypes` (`debug`, `staging`, `release`) sin flavors. El entorno y lo depurable quedan atados en el nombre y no hay dónde poner las variables de cada entorno.

### Paquete y nombre por entorno (AND-007)

| Flavor | `applicationId` | Nombre visible |
|--------|-----------------|----------------|
| `dev` | `com.pluk.reader.dev` | Tuck Dev |
| `stage` | `com.pluk.reader.stage` | Tuck Stage |
| `beta` | `com.pluk.reader.beta` | Tuck Beta |
| `prod` | `com.pluk.reader` | Tuck |

- `applicationIdSuffix` en `dev`, `stage` y `beta`; el `namespace` (paquete del código y de `R`/`BuildConfig`) sigue siendo `com.pluk.reader` en todos.
- El nombre visible sale de `src/<flavor>/res/values/strings.xml`, que pisa `app_name` de `main`.
- El ícono de las variantes que no se publican lleva una etiqueta, como Chrome Beta: DEV (`dev`), STAGE (`stage`) y BETA (`beta`); `prod` usa el de `main`. Lo genera `code/android/tools/make_env_icons.py` en `src/<flavor>/res/drawable/` (vector, texto en trazados de Host Grotesk). Si cambia el ícono de `main`, se vuelve a correr.

### Configuración por entorno en un solo lugar (AND-008)

- Cada flavor declara sus valores en `build.gradle.kts`: `BuildConfig.ENVIRONMENT` (`dev`, `stage`, `beta`, `prod`) y el host de los enlaces de la cuenta (`authLinkHost`, que va al manifiesto y a `R.string.auth_link_host`, ADR 0014). Una variable nueva por entorno se agrega ahí.
- El dominio no lee `BuildConfig`: recibe un `AppEnvironment` (enum `DEV`, `STAGE`, `BETA`, `PROD`) que provee Hilt desde `di`. La lógica que dependa del entorno se prueba en JVM con el valor que haga falta.
- Perfil muestra el entorno junto a la versión cuando no es producción (por ejemplo `1.0 · dev`), para saber qué build está abierto.

### Firebase: un solo proyecto por ahora

- Los cuatro paquetes se registran como apps Android en el proyecto actual, `mirror-reading-staging`. Por ahora comparten usuarios, libros y Remote Config: el entorno separa los paquetes, no los datos.
- Un solo `app/google-services.json` con los cuatro clientes (el plugin elige el del `applicationId` de la variante). Sigue fuera del repo (ADR 0005).
- Cuando exista un proyecto de producción aparte, su archivo va en `app/src/prod/google-services.json`, que tiene prioridad sobre el de `app/` para los builds de `prod`. Es una tarjeta aparte: exige desplegar reglas, Functions y Remote Config en el proyecto nuevo.
- Ingreso con Google (ADR 0014): cada app registrada necesita el SHA-1 de la firma de debug de la máquina (también beta, firmada con ella por ahora).
- Crashlytics (ADR 0018) y Remote Config (ADR 0016) separan los reportes y las condiciones por app registrada, sin cambios de código.
- Fuente: documentación del plugin de Google Services ("Adding the JSON File": ubicación por flavor y tipo de build) y de Android ("Configure build variants").

## Consecuencias

- Los comandos cambian de nombre: `./gradlew :app:testDevDebugUnitTest`, `:app:connectedDevDebugAndroidTest`, `:app:installDevDebug`. AGENTS.md y los comandos de `adb` (`run-as com.pluk.reader.dev`, `pm clear com.pluk.reader.dev`) se actualizan.
- Un clon nuevo necesita bajar el `google-services.json` después de registrar las apps nuevas; uno sin el cliente de un paquete no compila esa variante ("No matching client found").
- Los datos locales (Room, DataStore, libros) son de cada paquete: pasar de la variante vieja a `devDebug` empieza con la app vacía. Los libros con cuenta vuelven por la sincronización.
- Los enlaces de verificación de email (ONB-018) abren la app que tenga el host; con varias instaladas Android ofrece elegir hasta que exista `assetlinks.json` (K-157), que debe listar los cuatro paquetes.
