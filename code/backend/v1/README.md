# Backend v1 (Firebase)

Reglas de seguridad, índices y tests del backend de Pluk Reader. Decisión: `specs/adr/0007-backend-firebase.md`. Plan: `specs/plans/2026-10-07-backend-firebase.md`.

Proyecto de Firebase: `mirror-reading-staging` (región `us-central1`). Los tests usan el proyecto de demostración `demo-pluk-reader`, así que nunca tocan recursos reales.

## Requisitos

- Node.js 20 o superior y Firebase CLI (`firebase --version`, probado con 15.13.0).
- **JDK 21 o superior** para los emuladores. La documentación de Firebase dice "11 o superior", pero `firebase-tools` 15 rechaza versiones menores a 21. Si no tenés uno, el JDK de Android Studio sirve:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Comandos

Desde `code/backend/v1`:

| Comando | Qué hace |
|---|---|
| `npm install` | Instala dependencias de los tests. |
| `npm test` | Arranca los emuladores (Auth 9099, Firestore 8080, Storage 9199), corre los tests de reglas y los apaga. |
| `npm run emulators` | Deja los emuladores corriendo, con la interfaz en http://localhost:4000. |

## Estructura

- `firestore.rules`, `storage.rules`: reglas de seguridad. Hoy deniegan todo; las reales llegan en K-045 y K-046.
- `firestore.indexes.json`: índices.
- `tests/`: tests de reglas con `@firebase/rules-unit-testing`. Cada requisito se referencia por su ID en el nombre del test.

## Credenciales

`google-services.json`, `GoogleService-Info.plist` y claves de cuentas de servicio no se guardan en el repo (ver `.gitignore`).
