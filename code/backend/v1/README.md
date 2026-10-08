# Backend v1 (Firebase)

Reglas de seguridad, índices y tests del backend de Pluk Reader. Decisión: `specs/adr/0007-backend-firebase.md`. Plan: `specs/plans/2026-10-07-backend-firebase.md`.

Proyecto de Firebase: `mirror-reading-staging` (Firestore y cuentas en `us-central1`; el bucket de Storage por defecto y sus funciones en `us-east1`, porque una función debe estar en la región de su bucket). Los tests usan el proyecto de demostración `demo-pluk-reader`, así que nunca tocan recursos reales.

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

## Cuentas (pasos manuales en la consola)

Estos pasos se hacen una vez por proyecto de Firebase, en la consola web:

1. Authentication → Método de acceso: activar **Correo/contraseña** y **Google** (Google pide un correo de soporte del proyecto).
2. Configuración del proyecto → Tus apps → agregar app Android con el paquete `com.pluk.reader` y la huella **SHA-1** (y SHA-256) del certificado de firma. Sin ella, Google Sign-In falla en el teléfono.
   - Clave de debug: `keytool -J-Duser.language=en -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android` (el `-J-Duser.language=en` evita un error de `keytool` con la configuración regional en español).
   - Antes de publicar hay que agregar también la huella de la clave de release o de Play App Signing.
3. `google-services.json` se descarga en la rebanada de sincronización y no va al repo.

Al registrarse (email o Google), la función `onUserCreated` crea `users/{uid}` con el plan gratuito. La cuota gratuita (15 MiB) está en `functions/src/accounts.ts`.

## Despliegue

Se despliega con el MCP de Firebase o con `firebase deploy` desde esta carpeta, siempre a pedido del usuario. Las portadas (`users/{uid}/covers/`, LIB-012) solo funcionan después de desplegar `storage.rules` (`firebase deploy --only storage --project mirror-reading-staging`). Las funciones de Storage van en `us-east1` y la de cuentas en `us-central1`.

La política de limpieza de imágenes de build (borra las de más de 1 día) ya está puesta en ambas regiones (2026-10-08). Si se agrega una región nueva, correr `firebase functions:artifacts:setpolicy --location <región> --project mirror-reading-staging`; sin eso el despliegue termina con un aviso de error aunque las funciones sí se suban.

## Credenciales

`google-services.json`, `GoogleService-Info.plist` y claves de cuentas de servicio no se guardan en el repo (ver `.gitignore`).

## Dependencias

`npm audit` en `functions/` y `npm audit --omit=dev` en la raíz: 0 vulnerabilidades.

Excepción documentada: `npm audit` completo en la raíz marca 5 de severidad alta en `@grpc/grpc-js`, que llega con `@firebase/rules-unit-testing` y `firebase` (solo desarrollo; los tests hablan con el emulador local, no con servicios reales). La corrección propuesta es `npm audit fix --force`, un cambio mayor, y no se aplica a ciegas. Revisar el 2027-01-07 o cuando salga una versión de `@firebase/rules-unit-testing` que lo resuelva.
