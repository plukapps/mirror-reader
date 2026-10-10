# ADR 0014 — Autenticación en Android: Firebase Auth con email y Google

**Estado:** provisional. Fecha: 2026-10-10.

## Contexto

El onboarding (`specs/product/09-onboarding.md`, diseño O1 a O9) necesita alta con email y con Google, verificación del email, ingreso, recuperación de contraseña y un recordatorio diario. El backend es Firebase (ADR 0007) y la app ya usa Firebase Auth para la sesión, con una cuenta de desarrollo cargada desde `local.properties` (K-052) "mientras no existan las pantallas de cuenta".

El diseño muestra cosas que Firebase Auth no resuelve solo: un código de 6 dígitos por email (O3), "intentos restantes" al fallar (O7), huella (O7) y Apple (O2, O7).

## Decisión

1. **Firebase Auth** (SDK de Android, BoM 35) con dos proveedores: **email y contraseña**, y **Google**. Sin Apple ni huella en Android por ahora (ACC lo deja para iOS; la huella pide guardar credenciales y no está en el spec).
2. **Google con Credential Manager** (`androidx.credentials:credentials` y `credentials-play-services-auth` 1.6.0, `com.google.android.libraries.identity.googleid:googleid` 1.2.1), con `GetGoogleIdOption` y el cliente web (`R.string.default_web_client_id`, que genera el plugin de Google Services). El token de Google se cambia por una credencial de Firebase (`GoogleAuthProvider.getCredential`). Documentación: firebase.google.com/docs/auth/android/google-signin. Credential Manager necesita una `Activity`: la pantalla obtiene el token y el repositorio inicia sesión con él.
3. **Verificación por enlace**, no por código: `sendEmailVerification()` y la app consulta `reload()` al volver a primer plano y cada 3 s mientras O3 está a la vista. Un código de 6 dígitos exigiría una Cloud Function y un proveedor de correo propios.
4. **Recuperación de contraseña por enlace** (`sendPasswordResetEmail`). La pantalla O9 se abre desde el enlace con un App Link sobre la URL de acción de Firebase (`https://<proyecto>.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=…`) y usa `verifyPasswordResetCode` y `confirmPasswordReset`. Hasta publicar `/.well-known/assetlinks.json` en Firebase Hosting (paso manual, tarjeta propia), Android abre el enlace en el navegador y la página de Firebase hace el cambio: funciona igual, sin O9.
5. **Errores**: el SDK se traduce en `data` a un `AuthError` de dominio (credenciales inválidas, email en uso, cuenta deshabilitada, demasiados intentos, sin conexión, otro). Con la protección contra enumeración de emails, Firebase no dice cuántos intentos quedan ni si el email existe: se muestra "Email o contraseña incorrectos."
6. **Se quita la cuenta de desarrollo** (`DevAccountSignIn`, campos de `BuildConfig`): ya hay pantallas para iniciar sesión.
7. **Intereses, meta y recordatorio** se guardan en el dispositivo (DataStore). No van a Firestore: no hay catálogo que los use y `users/{uid}` no admite escrituras del cliente.
8. **Recordatorio diario** con `AlarmManager.setInexactRepeating` (sin permiso de alarmas exactas, sin WorkManager) y un `BroadcastReceiver` que publica la notificación; otro receptor lo reprograma tras `BOOT_COMPLETED`. Android 13+ pide `POST_NOTIFICATIONS`.

## Consecuencias

- Tres dependencias nuevas (Credential Manager y Google ID). Sin ellas no hay Google en Android.
- Para Google en otro equipo o en release hay que registrar su SHA-1 en Firebase (el de debug de esta máquina ya está).
- Un usuario puede quedar con cuenta sin verificar si abandona en O3: al volver a abrir la app ve O3 (ONB-019). Las reglas del backend aún no exigen email verificado (K-050).
- O9 dentro de la app depende del paso manual de `assetlinks.json`.
- Intereses y meta no viajan entre dispositivos (fuera de alcance de ONB).
