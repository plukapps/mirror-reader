# ADR 0007 — Backend: Firebase

**Estado:** provisional, revisable antes de construir la rebanada de sincronización (K-010). Reemplaza al ADR 0003.

## Contexto

Desarrollador único. El backend necesita cuentas, datos de usuario (biblioteca, colecciones, anotaciones, progreso) y almacenamiento de archivos EPUB con aislamiento por usuario. El ADR 0003 eligió Supabase. El desarrollador ya conoce Firebase y quiere arrancar con lo que domina.

## Opciones

1. Firebase (Auth, Firestore, Cloud Storage for Firebase).
2. Supabase (ADR 0003).
3. Firebase para cuentas y datos, y Cloudflare R2 para los archivos.

## Decisión

Firebase para cuentas, datos y archivos. El código del backend vive en `code/backend/v1` (reglas de seguridad, índices, configuración y, si hacen falta, funciones).

## Razones

- Es lo que el desarrollador conoce: menos tiempo hasta la primera sincronización.
- Sin servidores propios. Auth, base de datos y archivos bajo un solo proyecto.
- Las reglas de seguridad por usuario valen para todos los clientes (Android, iOS, web).
- Firestore tiene caché local y modo sin conexión, que ayuda al modelo offline-first (ADR 0002).

## Consecuencias

- **Reemplaza el ADR 0003.** Cambiar de backend no cambia los specs de producto.
- **Cloud Storage for Firebase exige el plan Blaze** (tarjeta) desde febrero de 2026. Cuota gratis: 5 GB almacenados y 100 GB de descargas al mes. Verificar contra la documentación oficial antes de construir.
- **Datos en Firestore, no en Postgres.** El modelo de datos de biblioteca, colecciones y anotaciones se rediseña como documentos. Las reglas de conflicto de `product/04-sync.md` (SYN) siguen valiendo y las implementa nuestro código de sincronización.
- **Android usa el SDK de Firebase** (con el BoM) en lugar de Retrofit, OkHttp y Gson para hablar con el backend. Esto cambia el ADR 0005: el stack de backend de Android se actualiza en la rebanada de sincronización. Retrofit, OkHttp y Gson no se agregan salvo que otra necesidad los pida.
- **Dependencia del proveedor.** Firestore no es migrable con facilidad. Para limitarla: los modelos y las interfaces de repositorio de `domain` no nombran Firebase, y los archivos pasan por una interfaz propia (`BookFileStore`). Así, mover los archivos a otro almacén (por ejemplo R2) no toca el resto.
- Cuota de espacio por usuario (ACC-002) y su tamaño: pregunta abierta #2 en `specs/open-questions.md`. La cuota se cuenta en Firestore, no en el almacén.
- El proyecto de Firebase (ids, `google-services.json`) no se guarda en el repo si contiene credenciales. Ver `security-and-hardening` antes de la rebanada de sync.
