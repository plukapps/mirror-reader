# Plan: marca de la app en Perfil

Requisito: PRF-024 (`specs/product/11-profile.md`). Tarjeta: K-338. Rama `claude/profile-app-icon-name-a99a8f`.

Pedido del usuario (2026-10-16): el ícono y el nombre de la app arriba de Perfil, antes de la tarjeta del usuario. Hoy la app no tiene ícono propio (el lanzador de Android es el robot por defecto y el de iOS está vacío), así que se usa la marca que ya existe: el logo de cuatro barras y "tuck.", el mismo componente de la bienvenida (decisión del usuario).

## Tareas

1. **Android.** `Brand` (onboarding) acepta un color y tiñe el logo con él (en el onboarding sigue igual: la tinta clara). `ProfileContent` lo muestra antes de la cabecera, con `MarginColors.Ink` y margen de 24 dp a los lados, como los títulos de sección. Test: `ProfileScreenTest` comprueba que "tuck." aparece y se anuncia como un solo elemento (PRF-024). Instrumentado: se compila; correrlo donde diga el usuario.
2. **iOS.** `ProfileContent` muestra `Brand()` antes de la cabecera, con `MarginColors.ink` y los mismos márgenes. No hay tests de vista de Perfil en Apple; se verifica a mano en el iPhone.
3. **Verificación a mano.** Perfil en el teléfono y el iPhone, en modo claro y oscuro.

## Fuera de alcance

El ícono real del lanzador (Android e iOS): tarjeta aparte.
