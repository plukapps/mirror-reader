# ADR 0021 — Entornos de build en Apple (dev, stage, beta, prod)

**Estado:** provisional. Sigue al ADR 0020 (entornos en Android). Depende de los ADR 0010 (proyecto con XcodeGen) y 0012 (Firebase en Apple).

## Contexto

Android ya tiene cuatro entornos que se instalan juntos (ADR 0020). Pedido del usuario (2026-10-16): lo mismo en iOS, en una rama y un PR aparte. El proyecto de Apple es un solo target para Mac e iPhone, descrito en `project.yml` (XcodeGen), con las configuraciones `Debug` y `Release`, un solo `App/GoogleService-Info.plist` que no va al repo, y el esquema de URL de Google en `Signing.local.xcconfig`.

## Decisiones

### Una configuración de build por entorno

En Xcode el equivalente a un flavor de Android es una configuración de build. `Debug` y `Release` se reemplazan por cuatro, con el mismo tipo que en Android (APL-001):

| Configuración | Tipo | Bundle ID | Nombre visible | Esquema |
|---------------|------|-----------|----------------|---------|
| `Dev` | debug | `com.pluk.reader.dev` | Tuck Dev | `Reader` |
| `Stage` | debug | `com.pluk.reader.stage` | Tuck Stage | `Reader Stage` |
| `Beta` | release | `com.pluk.reader.beta` | Tuck Beta | `Reader Beta` |
| `Prod` | release | `com.pluk.reader` | Tuck | `Reader Prod` |

- Cada esquema corre, prueba y archiva con su configuración. El esquema `Reader` usa `Dev` para correr y para los tests, así los comandos de siempre siguen sirviendo.
- La salida cambia de carpeta: `Build/Products/Dev-iphonesimulator/Tuck.app` en lugar de `Debug-iphonesimulator`.
- Mac e iPhone comparten el bundle ID de cada entorno, como hoy. `BUNDLE_ID_SUFFIX` (equipo personal, `Signing.xcconfig`) se sigue sumando al final.

### Configuración por entorno en un solo lugar (APL-002)

- Los valores de cada entorno son ajustes de build por configuración en `project.yml`: `APP_ENVIRONMENT` (`dev`, `stage`, `beta`, `prod`), `ENV_BUNDLE_SUFFIX`, el nombre visible y el ícono. Una variable nueva por entorno se agrega ahí.
- `APP_ENVIRONMENT` llega al `Info.plist` (clave `AppEnvironment`). La app la lee al arrancar y la pasa como `AppEnvironment` (enum del paquete `ReaderDomain`, `dev`, `stage`, `beta`, `prod`) desde `AppGraph`; el dominio no lee el `Bundle`. Un valor desconocido detiene la app al arrancar, como un flavor mal configurado detiene el build en Android.
- Perfil muestra `tuck. 0.1.0 · dev` fuera de producción, igual que Android.

### Firebase: un archivo por entorno

- Cada bundle ID se registra como app de iOS en `mirror-reading-staging` (como en Android, por ahora los cuatro entornos comparten el proyecto). Para la Mac sirve la misma app.
- El `GoogleService-Info.plist` de cada app lleva su bundle ID y su cliente de Google, así que hay uno por entorno: `App/Firebase/GoogleService-Info-<Configuración>.plist` (`-Dev`, `-Stage`, `-Beta`, `-Prod`), fuera del repo y fuera de los recursos del target. Una fase de build copia el de la configuración como `GoogleService-Info.plist` dentro de la app. Sin el archivo, la app sigue funcionando solo en local (ADR 0012) y la fase lo avisa.
- La fase de Crashlytics (ADR 0018) usa el archivo de la configuración.
- El esquema de URL de vuelta de Google (ADR 0015) cambia por entorno: `Signing.local.xcconfig` guarda `GOOGLE_REVERSED_CLIENT_ID_DEV`, `_STAGE`, `_BETA` y `_PROD`, y cada configuración elige el suyo. `tools/copy-google-client.sh` los copia de los cuatro archivos.

### Ícono con la etiqueta del entorno

- Como en Android: DEV, STAGE y BETA sobre el ícono, al estilo de Chrome Beta; prod sin etiqueta. Un catálogo por entorno (`AppIcon-Dev`, `AppIcon-Stage`, `AppIcon-Beta`) y `ASSETCATALOG_COMPILER_APPICON_NAME` por configuración.
- Lo genera `tools/make_env_icons.swift` (CoreGraphics y CoreText, sin dependencias) a partir de `AppIcon.appiconset`: el ícono de iPhone (claro, oscuro y tintado) y los diez tamaños de Mac. El texto es Host Grotesk Bold de `App/Fonts`. Si cambia el ícono, se vuelve a correr.

### Firma

- Sin cambios (ADR 0010): firma local en el simulador y con el equipo de `Signing.local.xcconfig` en la Mac y el iPhone. `Beta` y `Prod` son builds de release firmados igual; la firma para distribuir llega con la cuenta de Apple Developer (K-080).

## Alternativas descartadas

- **Un target por entorno:** duplica fuentes, dependencias y fases en `project.yml`.
- **Mantener `Debug`/`Release` y un ajuste aparte para el entorno:** no se pueden tener dos bundle IDs del mismo tipo de build sin tocar ajustes a mano antes de cada build.
- **Un solo `GoogleService-Info.plist`:** en iOS el archivo es de una sola app (a diferencia del `google-services.json` de Android, que trae varios clientes).

## Consecuencias

- Los comandos de `xcodebuild` con `-scheme Reader` siguen funcionando y usan `Dev`. Para otro entorno: `-scheme "Reader Beta"`, etc. Las rutas de `Build/Products` cambian (README y AGENTS).
- Cada máquina baja los cuatro `GoogleService-Info-*.plist` a `App/Firebase/` (el viejo `App/GoogleService-Info.plist` ya no se usa) y vuelve a correr `tools/copy-google-client.sh`.
- Cada bundle ID tiene sus propios datos (SwiftData, preferencias, llavero): un entorno nuevo arranca vacío. En el simulador `com.pluk.reader` (la instalación anterior) queda como la app de producción.
