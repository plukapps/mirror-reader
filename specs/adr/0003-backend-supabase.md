# ADR 0003 — Backend: Supabase

**Estado:** provisional, revisable antes de construir la rebanada de sincronización.

## Contexto

Desarrollador único. Datos relacionales (biblioteca, colecciones, anotaciones). Necesita cuentas, base de datos y almacenamiento de archivos con aislamiento por usuario.

## Opciones

1. Supabase (Postgres, cuentas, almacenamiento gestionados).
2. Firebase.
3. Backend propio.

## Decisión

Supabase.

## Razones

- Postgres encaja con datos relacionales.
- Reglas de acceso por usuario definidas en la base de datos, válidas para todos los clientes.
- Código abierto, migrable. Costo predecible.
- Se evita operar servidores.

## Consecuencias

- Según ADR 0005, el cliente Android consume el backend con Retrofit, OkHttp y Gson mediante la API REST de Supabase (PostgREST para datos, GoTrue para cuentas, Storage para archivos). No se usa el SDK de Supabase para Kotlin, para no sumar un cliente fuera del stack definido. Esto se debe validar en la rebanada de sincronización.
- La lógica de sincronización la escribimos nosotros.
- Cambiar de backend implica reemplazar este ADR, no los specs de producto.
