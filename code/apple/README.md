# Reader para Apple (Mac e iOS)

App en Swift y SwiftUI, un solo target para Mac e iPhone. Por ahora son Inicio y la biblioteca con los libros reales de la cuenta, guardados en una base local con SwiftData y bajados de Firebase (plan `specs/plans/2026-10-09-ios-home-data.md`, ADR 0012).

## Requisitos

- Xcode 16.2 o posterior, activo: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
- XcodeGen: `brew install xcodegen`.
- Para la Mac y el iPhone físico hace falta el equipo de desarrollo en `Signing.local.xcconfig` (el gratuito alcanza, ver `Signing.xcconfig`): en la Mac Firebase Auth guarda la sesión en el llavero y con firma local falla. El simulador usa firma local. Al pasar de firma local a firma con equipo, macOS puede preguntar si la app nueva usa los datos de la anterior: hay que aceptar.
- Firebase (opcional; sin esto la app funciona solo en local):
  - `App/GoogleService-Info.plist`, que no va al repo. Se baja con `firebase apps:sdkconfig IOS <app id> --project mirror-reading-staging -o App/GoogleService-Info.plist` (app de iOS `com.pluk.reader`, `firebase apps:list`) o desde la consola.
  - Cuenta de desarrollo en `Signing.local.xcconfig` (solo Debug): `./tools/copy-dev-account.sh` la copia de `code/android/local.properties` sin mostrarla.
- El SDK de Firebase está fijo en 12.14.0: desde la 12.15 exige Xcode 16.3. El primer build lo descarga (varios minutos).

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
open .build/xcode/Build/Products/Debug/Reader.app

# Compilar y correr en el simulador de iPhone
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/xcode build
xcrun simctl boot "iPhone 16"; open -a Simulator
xcrun simctl install booted .build/xcode/Build/Products/Debug-iphonesimulator/Reader.app && xcrun simctl launch booted com.pluk.reader

# Tests de la app; corren solo en local, nunca contra Firebase (cambiar el destino para correrlos en la Mac: 'platform=macOS')
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/xcode test

# En un iPhone físico (cuenta gratuita de Apple): una vez por máquina, crear Signing.local.xcconfig
# con DEVELOPMENT_TEAM (ver Signing.xcconfig), agregar el Apple ID en Xcode → Settings → Accounts
# y activar el Modo de desarrollador en el iPhone. La app instalada así vence a los 7 días.
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'generic/platform=iOS' -derivedDataPath .build/xcode -allowProvisioningUpdates build
xcrun devicectl device install app --device <id de devicectl list devices> .build/xcode/Build/Products/Debug-iphoneos/Reader.app

# Tests del dominio
cd Packages/ReaderDomain && swift test
```
