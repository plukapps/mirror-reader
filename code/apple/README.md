# Reader para Apple (Mac e iOS)

App en Swift y SwiftUI, un solo target para Mac e iPhone. Por ahora es una prueba de Inicio con datos falsos (plan `specs/plans/2026-10-08-apple-home-spike.md`).

## Requisitos

- Xcode 16.2 o posterior, activo: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.
- XcodeGen: `brew install xcodegen`.
- No hace falta cuenta de Apple Developer para correr en la propia Mac (firma local).

## Estructura

```
project.yml                 descripción del proyecto (XcodeGen); el .xcodeproj se genera y no va al repo
App/                        target de la app: punto de entrada, UI, datos
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
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=macOS' -derivedDataPath .build/xcode build
open .build/xcode/Build/Products/Debug/Reader.app

# Compilar y correr en el simulador de iPhone
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/xcode build
xcrun simctl boot "iPhone 16"; open -a Simulator
xcrun simctl install booted .build/xcode/Build/Products/Debug-iphonesimulator/Reader.app && xcrun simctl launch booted com.pluk.reader

# Tests de la app (cambiar el destino para correrlos en la Mac: 'platform=macOS')
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/xcode test

# En un iPhone físico (cuenta gratuita de Apple): una vez por máquina, crear Signing.local.xcconfig
# con DEVELOPMENT_TEAM (ver Signing.xcconfig), agregar el Apple ID en Xcode → Settings → Accounts
# y activar el Modo de desarrollador en el iPhone. La app instalada así vence a los 7 días.
xcodebuild -project Reader.xcodeproj -scheme Reader -destination 'generic/platform=iOS' -derivedDataPath .build/xcode -allowProvisioningUpdates build
xcrun devicectl device install app --device <id de devicectl list devices> .build/xcode/Build/Products/Debug-iphoneos/Reader.app

# Tests del dominio
cd Packages/ReaderDomain && swift test
```
