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
- `functions/`: Cloud Functions en TypeScript (Node 22). Contabilidad de la cuota de espacio (ADR 0008): `onBookFileFinalized` y `onBookFileDeleted` mantienen `users/{uid}.usedBytes`. La lógica está en `src/quota.ts`, separada de los disparadores. Se compila con `npm --prefix functions run build` (`npm test` ya lo hace).
- `tests/`: tests de reglas y de punta a punta con el emulador de Functions. `functions/tests/`: tests de la lógica de contabilidad contra el emulador de Firestore. Tests de reglas con `@firebase/rules-unit-testing`. Cada requisito se referencia por su ID en el nombre del test.

## Notas de los tests

- Los archivos de test corren **uno a uno** (`--test-concurrency=1`): comparten un único emulador y `clearFirestore()` de un archivo borraría los datos de otro.
- `clearStorage()` no borra los archivos en este emulador. Los tests de Storage usan un hash nuevo por test.
- El tamaño del objeto llega como **texto** en los eventos de Storage aunque los tipos de `firebase-functions` digan `number`. `parseObjectSize` lo valida.
- El cliente de pruebas sube por defecto al bucket `demo-pluk-reader`, pero las funciones escuchan el bucket por defecto (`demo-pluk-reader.appspot.com`). El test de punta a punta indica el bucket explícito.
- En Storage, `create` cubre cualquier escritura de contenido, también sobre un archivo existente. Para no sobrescribir, la regla exige `resource == null`.

## Credenciales

`google-services.json`, `GoogleService-Info.plist` y claves de cuentas de servicio no se guardan en el repo (ver `.gitignore`).

## Dependencias

`npm audit` en `functions/` y `npm audit --omit=dev` en la raíz: 0 vulnerabilidades.

Excepción documentada: `npm audit` completo en la raíz marca 5 de severidad alta en `@grpc/grpc-js`, que llega con `@firebase/rules-unit-testing` y `firebase` (solo desarrollo; los tests hablan con el emulador local, no con servicios reales). La corrección propuesta es `npm audit fix --force`, un cambio mayor, y no se aplica a ciegas. Revisar el 2027-01-07 o cuando salga una versión de `@firebase/rules-unit-testing` que lo resuelva.
