# 05 — Cuenta y planes

## Objetivo

Identidad para sincronizar, y base para un modelo de pago futuro.

## Requisitos

- **ACC-001** Debe requerir cuenta en la v1. Registro con email y con Google.
- **ACC-002** Cada usuario tiene un plan que define su cuota de espacio. La v1 tiene un solo plan gratuito (tamaño en open-questions #2).
- **ACC-003** Debe mostrar el espacio usado y disponible.
- **ACC-004** Cada usuario solo accede a sus propios datos.
- **ACC-005** El concepto de plan debe permitir agregar planes de pago sin cambiar el modelo de datos.
- **ACC-006** Debe permitir cerrar sesión y ver los dispositivos vinculados.

## Escenarios

- Dado que inicio sesión en un dispositivo nuevo, cuando termina el login, entonces veo mi biblioteca completa (libros "solo en la nube").
- Dado que llego a la cuota, cuando importo otro libro, entonces se bloquea con explicación y puedo seguir leyendo.

## Fuera de alcance en v1

Cobro, suscripción, uso sin cuenta, "Iniciar sesión con Apple" (se agrega con iOS).
