# Reader para Apple (Mac, después iOS)

App en Swift y SwiftUI. Por ahora es una prueba de Inicio con datos falsos (plan `specs/plans/2026-10-08-apple-home-spike.md`).

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

# Tests del dominio
cd Packages/ReaderDomain && swift test
```
