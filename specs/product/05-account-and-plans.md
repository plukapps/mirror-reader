# 05 — Cuenta y planes

## Objetivo

Identidad para sincronizar, y base para un modelo de pago futuro.

## Requisitos

- **ACC-001** Debe requerir cuenta en la v1. Registro con email y con Google.
- **ACC-002** Cada usuario tiene un plan que define su cuota de espacio. En la v1 todo usuario nuevo recibe el plan gratuito (tamaño en open-questions #2).
- **ACC-003** Debe mostrar el espacio usado y disponible.
- **ACC-004** Cada usuario solo accede a sus propios datos.
- **ACC-005** El concepto de plan debe permitir agregar planes de pago sin cambiar el modelo de datos.
- **ACC-006** Debe permitir cerrar sesión y ver los dispositivos vinculados.
- **ACC-007** Hay un catálogo de planes (por ahora `free`, `plus` y `pro`). Cada plan define: espacio total (en GB), tamaño máximo por archivo (en MB) y precio mensual y anual con su moneda (USD). Los valores iniciales son los de PLN-001 (Gratis = `free`, Lector = `plus`, Biblioteca = `pro`). Los valores se cambian sin publicar una versión nueva de la app.
- **ACC-008** Los límites que valen son los del servidor. La app usa y muestra los que el servidor le informa. Solo mientras todavía no los obtuvo usa valores propios por defecto, y estos nunca habilitan algo que el servidor rechace.
- **ACC-009** Al cambiar los valores de un plan, se aplican a todos los usuarios de ese plan. Nunca se borra nada: si el espacio usado queda por encima del nuevo total, se bloquean nuevas subidas (LIB-009) y se sigue leyendo.
- **ACC-010** Un archivo más grande que el máximo por archivo del plan se rechaza al subir, con una explicación que dice el máximo.
- **ACC-011** Las funciones que dependen del plan (por ahora, la sincronización de notas, SYN-017) se pueden prender o apagar desde la administración del producto, para todas las cuentas o por plan, sin publicar una versión nueva de la app. Mientras no haya cobro, todas las cuentas tienen todo prendido.

- La pantalla de planes, los avisos de cuota y Mi plan están en `12-plans.md` (PLN).

## Escenarios

- Dado que inicio sesión en un dispositivo nuevo, cuando termina el login, entonces veo mi biblioteca completa (libros "solo en la nube").
- Dado que llego a la cuota, cuando importo otro libro, entonces se bloquea con explicación y puedo seguir leyendo.
- Dado que el plan gratuito permite 2 MB por archivo, cuando intento subir un libro de 3 MB, entonces se rechaza y veo que el máximo es 2 MB.
- Dado que el plan gratuito baja su espacio total por debajo de lo que ya uso, cuando abro la app, entonces conservo y puedo leer todos mis libros, pero no puedo subir otros.
- Dado que la app todavía no obtuvo el catálogo (primer arranque sin conexión), cuando miro los planes, entonces veo los valores por defecto de la app.

## Fuera de alcance en v1

Cobro, suscripción y cambio de plan desde la app (el precio del catálogo es informativo: cuando haya cobro, el precio real y localizado lo fija cada tienda), uso sin cuenta, "Iniciar sesión con Apple" (se agrega con iOS).
