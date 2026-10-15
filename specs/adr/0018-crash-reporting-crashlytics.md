# ADR 0018 — Registro de cierres inesperados con Firebase Crashlytics

**Estado:** provisional. Depende del ADR 0007 (Firebase).

## Contexto

CMP-010 pide registrar los cierres inesperados para corregirlos antes y después de publicar. Hoy un cierre en el teléfono de alguien más solo se conoce si lo cuenta. Las dos apps ya usan Firebase (Auth, Firestore, Storage, Remote Config). Pedido del usuario (2026-10-15): integrar Crashlytics en Android e iOS.

## Decisiones

### Crashlytics, sin Google Analytics

- **Android:** `firebase-crashlytics` por el BoM 35 y el plugin de Gradle `com.google.firebase.crashlytics` 3.0.8 (sube los archivos de mapeo cuando se active la minificación). Se inicia solo al arrancar la app.
- **iOS y Mac:** producto `FirebaseCrashlytics` del mismo paquete fijo (12.14.0, ADR 0012). Se inicia con `FirebaseApp.configure()`; sin `GoogleService-Info.plist` (y en los tests) no hay Firebase y no se registra nada.
- **Sin Analytics:** la documentación lo recomienda para las migas de pan (qué hizo el usuario antes del cierre), pero suma recolección de datos que habría que declarar y justificar (CMP-002, CMP-009). Se puede sumar más adelante con su propio ADR.
- **Símbolos en iOS:** `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym` y la fase final `Crashlytics/run` del paquete, con los archivos de entrada que pide la documentación desde Xcode 15. La fase no hace nada si la app no tiene `GoogleService-Info.plist`: un clon sin credenciales sigue compilando.
- Fuente: documentación de Crashlytics, "Get started" de Android y de Apple, y "Get deobfuscated reports" (iOS).

### Identificador de la cuenta

- Cada reporte lleva el `uid` de Firebase (`setUserId` / `setUserID`): permite encontrar el cierre de quien escribe a soporte. Nunca el email ni el nombre (CMP-010).
- Al cerrar sesión (PRF-009) se manda un identificador vacío, así los cierres siguientes no quedan asociados a la cuenta anterior.
- La lógica vive en el dominio (`CrashReportIdentity`, con un `CrashReporter` que la capa `data` implementa con Crashlytics). Los tests usan un doble y nunca tocan Firebase.
- Sin claves ni registros propios por ahora: nada que pueda llevar texto de libros o notas (CMP-009).

### En builds de depuración también

Se registra en todas las variantes. Mientras la app no está publicada, los cierres de depuración son casi los únicos y sirven. Antes de publicar se puede apagar en depuración (`firebase_crashlytics_collection_enabled` en Android, `FirebaseCrashlyticsCollectionEnabled` en iOS).

## Alternativas descartadas

- **Sentry:** más completo en símbolos y rendimiento, pero suma un proveedor, una cuenta y otro SDK cuando Firebase ya está en las dos apps.
- **Solo los informes de las tiendas** (Play Console, Organizador de Xcode): no requieren nada, pero llegan solo de usuarios que lo permitieron, tarde y sin la cuenta asociada.

## Consecuencias

- Dependencias nuevas: `firebase-crashlytics` y su plugin en Android; `FirebaseCrashlytics` en iOS.
- La política de privacidad (K-156) declara los datos de diagnóstico de cierres, asociados al identificador de la cuenta. Lo mismo en la sección de seguridad de datos de Google Play y en las etiquetas de privacidad de la App Store.
- Crashlytics se habilita una vez en la consola de Firebase del proyecto real; es una acción que se pide al usuario.
