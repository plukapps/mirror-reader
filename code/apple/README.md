# Reader para Apple (Mac e iOS)

App en Swift y SwiftUI, un solo target para Mac e iPhone. Por ahora son Inicio, la biblioteca y, en iPhone, la búsqueda local, con los libros reales de la cuenta, guardados en una base local con SwiftData y bajados de Firebase (plan `specs/plans/2026-10-09-ios-home-data.md`, ADR 0012).

## Requisitos

- Xcode 16.2 o posterior, activo: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
- XcodeGen: `brew install xcodegen`.
- Para la Mac y el iPhone físico hace falta el equipo de desarrollo en `Signing.local.xcconfig` (el gratuito alcanza, ver `Signing.xcconfig`): en la Mac Firebase Auth guarda la sesión en el llavero y con firma local falla. El simulador usa firma local. Al pasar de firma local a firma con equipo, macOS puede preguntar si la app nueva usa los datos de la anterior: hay que aceptar.
- Firebase (opcional; sin esto la app funciona solo en local):
  - Un `GoogleService-Info` por entorno (ADR 0021) en `App/Firebase/`, que no va al repo: `GoogleService-Info-Dev.plist`, `-Stage`, `-Beta` y `-Prod`. Se bajan con `firebase apps:sdkconfig IOS <app id> --project mirror-reading-staging -o App/Firebase/GoogleService-Info-Dev.plist` (apps de iOS `com.pluk.reader.dev`, `.stage`, `.beta` y `com.pluk.reader`; `firebase apps:list`) o desde la consola. Una fase del build copia el de la configuración dentro de la app; sin él, avisa y la app funciona solo en local.
  - Ingreso con Google (ADR 0015): `./tools/copy-google-client.sh` copia el `REVERSED_CLIENT_ID` de cada plist a `Signing.local.xcconfig` (`GOOGLE_REVERSED_CLIENT_ID_DEV`, …: esquema de URL de vuelta de cada entorno); después, `xcodegen`. Sin él, el botón de Google avisa que falló. La app se usa sin cuenta ("Empezar a leer"); la cuenta se crea desde la bienvenida o desde Perfil. No hay cuenta de desarrollo. Para volver a ver la bienvenida en un simulador: `xcrun simctl uninstall <simulador> com.pluk.reader.dev`.
- El SDK de Firebase está fijo en 12.14.0: desde la 12.15 exige Xcode 16.3. El primer build lo descarga (varios minutos).

## Entornos

Una configuración de build por entorno (ADR 0021), cada una con su esquema. Se instalan juntas.

| Esquema | Configuración | Tipo | Bundle ID | Nombre |
|---------|---------------|------|-----------|--------|
| `Reader` | `Dev` | debug | `com.pluk.reader.dev` | Tuck Dev |
| `Reader Stage` | `Stage` | debug | `com.pluk.reader.stage` | Tuck Stage |
| `Reader Beta` | `Beta` | release | `com.pluk.reader.beta` | Tuck Beta |
| `Reader Prod` | `Prod` | release | `com.pluk.reader` | Tuck |

- Lo que cambia por entorno va en `settings.configs` del target `Reader` en `project.yml`. La app lo lee del `Info.plist` (`AppEnvironment`) con `BuildEnvironment.current()`.
- Los tests corren con `Reader` (Dev). Los productos quedan en `Build/Products/<Configuración>[-iphonesimulator]`.
- El ícono de Dev, Stage y Beta lleva una etiqueta. Si cambia `AppIcon`, regenerarlos con `swift tools/make_env_icons.swift`.

## Estructura

```
project.yml                 descripción del proyecto (XcodeGen); el .xcodeproj se genera y no va al repo
App/                        target de la app: punto de entrada (ReaderApp, AppGraph), UI, datos
  Data/Local/               base local (SwiftData) y archivos de la biblioteca
  Data/Remote/              Firebase: sesión, libros, posiciones y portadas
Packages/ReaderDomain/      modelos y reglas sin UI (paquete Swift), con sus tests
```

## Uso

Desde `code/apple`:

```bash
xcodegen                    # genera Reader.xcodeproj (repetir si cambia project.yml o se agregan archivos)
open Reader.xcodeproj       # en Xcode, ⌘R corre la app y ⌘U los tests
```

Desde la terminal, sin abrir Xcode:

```bash
# Compilar la app
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=macOS' -derivedDataPath .build/xcode -allowProvisioningUpdates build
open .build/xcode/Build/Products/Dev/Tuck.app

# Compilar y correr en el simulador de iPhone
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/xcode build
xcrun simctl boot "iPhone 16"; open -a Simulator
xcrun simctl install booted .build/xcode/Build/Products/Dev-iphonesimulator/Tuck.app && xcrun simctl launch booted com.pluk.reader.dev

# Tests de la app; corren solo en local, nunca contra Firebase (cambiar el destino para correrlos en la Mac: 'platform=macOS')
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/xcode test

# En un iPhone físico (cuenta gratuita de Apple): una vez por máquina, crear Signing.local.xcconfig
# con DEVELOPMENT_TEAM (ver Signing.xcconfig), agregar el Apple ID en Xcode → Settings → Accounts
# y activar el Modo de desarrollador en el iPhone. La app instalada así vence a los 7 días.
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'generic/platform=iOS' -derivedDataPath .build/xcode -allowProvisioningUpdates build
xcrun devicectl device install app --device <id de devicectl list devices> .build/xcode/Build/Products/Dev-iphoneos/Tuck.app

# Tests del dominio
cd Packages/ReaderDomain && swift test
```
