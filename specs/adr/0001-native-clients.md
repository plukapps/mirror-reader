# ADR 0001 — Clientes nativos por plataforma

**Estado:** aceptado

## Contexto

Producto para Android, iOS, Mac y web. Un EPUB es HTML y CSS, así que cada cliente lo renderiza sobre un webview.

## Opciones

1. Un código web empaquetado para cada plataforma.
2. Cross-platform con UI propia (Flutter, React Native).
3. Nativo por plataforma.

## Decisión

Nativo: Kotlin en Android, Swift en iOS y Mac, web aparte.

## Consecuencias

- Mejor experiencia en cada plataforma.
- Más código que mantener, a cargo de una sola persona. Por eso se construye por fases, empezando por Android.
- El spec es el contrato común. Los criterios de aceptación no dependen de la plataforma.
- iOS y Mac pueden compartir código Swift.
- El stack y la arquitectura de Android se concretan en ADR 0005.
