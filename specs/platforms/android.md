# Plataforma — Android (v1)

Primera plataforma en construirse. Distribución: Google Play.

## Alcance

Todo lo definido en `product/`: biblioteca, lector, anotaciones, sincronización, cuenta y cumplimiento.

## Requisitos específicos

- **AND-001** Debe funcionar en teléfono y tablet.
- **AND-002** Debe ser compatible con TalkBack y respetar el tamaño de fuente del sistema.
- **AND-003** Debe recibir EPUB desde "Compartir / Abrir con" (LIB-001).
- **AND-004** Debe cumplir las políticas de Google Play (incluida la eliminación de cuenta, CMP-001).
- **AND-005** La pantalla del lector es una pantalla de Compose dentro de una sola actividad. Ver ADR 0005.

## Arquitectura

Stack y capas definidos en `specs/adr/0005-android-stack-and-architecture.md`: Compose, Navigation Compose, MVVM en capas (ui, domain, data, di), Hilt, Coroutines y Flow, Room, DataStore y, para el backend, Retrofit, OkHttp y Gson.

## Decisiones

- Versión mínima de Android: 26 (Android 8.0).
- Idiomas de la interfaz: pendiente (open-questions #4).

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en un dispositivo real, y beta cerrada aprobada en Google Play.
