# ADR 0015 — Autenticación en Apple: Firebase Auth con email y Google

**Estado:** provisional. Fecha: 2026-10-12.

## Contexto

El onboarding (`specs/product/09-onboarding.md`, O1 a O9) ya existe en Android (ADR 0014). La app de Apple (ADR 0010, 0012) usa Firebase Auth solo para la sesión, con una cuenta de desarrollo cargada desde `Signing.local.xcconfig` (solo Debug) "mientras no existan las pantallas de cuenta". El pedido es el mismo onboarding en iOS, tal cual Android.

La máquina de desarrollo tiene Xcode 16.2 (Swift 6.0) y no hay cuenta paga de Apple Developer: no se pueden usar Associated Domains (Universal Links) ni "Iniciar sesión con Apple".

## Decisión

1. **Firebase Auth** (SDK 12.14.0, ADR 0012) con **email y contraseña** y **Google**, igual que Android. Las reglas (contraseña, email, arranque, intereses, meta) viven en `ReaderDomain` con los mismos casos de test que Android.
2. **Google con el SDK GoogleSignIn-iOS 9.1.0** (fijo: `swift-tools-version` 6.0, compila con Xcode 16.2; comparte `gtm-session-fetcher`, `app-check` y `GoogleUtilities` con Firebase en rangos compatibles). Documentación: firebase.google.com/docs/auth/ios/google-signin. El `clientID` sale de `FirebaseApp.app()?.options.clientID` y el esquema de URL de retorno (`REVERSED_CLIENT_ID` de `GoogleService-Info.plist`) va en el `Info.plist` desde la variable `GOOGLE_REVERSED_CLIENT_ID` de `Signing.local.xcconfig`, que copia `tools/copy-google-client.sh` (ninguno de los dos archivos va al repo). El ID token y el access token se cambian por una credencial de Firebase (`GoogleAuthProvider.credential(withIDToken:accessToken:)`). Mac e iPhone usan el mismo código.
3. **Verificación por enlace** (`sendEmailVerification`), con `reload()` al volver a primer plano y cada 3 s mientras O3 está a la vista, como Android.
4. **Recuperación por enlace** (`sendPasswordReset`). La pantalla O9 existe y se abre si la app recibe la URL de acción de Firebase (`https://<proyecto>.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=…`), con `verifyPasswordResetCode` y `confirmPasswordReset`. Sin cuenta paga no hay Associated Domains: hoy el enlace abre Safari y la página de Firebase hace el cambio (igual que Android sin `assetlinks.json`). Queda una tarjeta para el `apple-app-site-association`.
5. **Errores**: `AuthErrorCode` de Firebase se traduce en `App/Data` a `AuthError` de dominio (los mismos casos que Android). Con la protección contra enumeración, "Email o contraseña incorrectos." sin más detalle.
6. **Se quita la cuenta de desarrollo** (`DevCredentials`, `DevAccountEmail` y `DevAccountPassword` del `Info.plist`, `tools/copy-dev-account.sh`): `LibrarySync` sincroniza solo si hay sesión.
7. **Intereses, meta y recordatorio en el dispositivo** (`UserDefaults`, con las claves de Android). No van a Firestore (ADR 0014).
8. **Recordatorio diario con notificaciones locales** (`UNUserNotificationCenter`, `UNCalendarNotificationTrigger` diario que se repite). El sistema lo conserva tras reiniciar, sin receptor propio. El permiso se pide al tocar "Terminar" (ONB-011).
9. **Sin Firebase configurado** (sin `GoogleService-Info.plist`, o en los tests), la app arranca en Inicio y funciona solo en local (ADR 0002): no hay cuenta que crear.
10. **Sin "Iniciar sesión con Apple"**: exige cuenta paga (capacidad *Sign in with Apple*). La App Store lo pide cuando hay ingreso con Google (guía 4.8), así que es requisito antes de publicar (tarjeta propia, junto con K-080).

## Consecuencias

- Una dependencia nueva (GoogleSignIn y sus dependencias AppAuth y GTMAppAuth).
- Para que Google funcione en una máquina nueva hay que correr `tools/copy-google-client.sh` después de bajar `GoogleService-Info.plist`; sin el esquema de URL, el botón de Google avisa que falló.
- O9 dentro de la app depende de Associated Domains (cuenta paga).
- Publicar en la App Store exige agregar "Iniciar sesión con Apple".
