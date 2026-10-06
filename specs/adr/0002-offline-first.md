# ADR 0002 — Offline-first

**Estado:** aceptado

## Contexto

Leer no debe depender de internet. El problema original es la falta de sincronización confiable.

## Decisión

Cada dispositivo guarda todo localmente y escribe primero ahí. Un proceso de sincronización envía y recibe cambios cuando hay conexión. Reglas de conflicto en `product/04-sync.md`.

## Consecuencias

- La lectura y las anotaciones funcionan sin conexión.
- Hay que implementar cola de cambios, marcas de borrado y resolución de conflictos en cada cliente.
- El contrato de sincronización debe ser idéntico en todas las plataformas.
