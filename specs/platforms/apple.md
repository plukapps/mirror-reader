# Plataforma — Apple: Mac e iOS (borrador)

Mac primero, iOS después, desde el mismo proyecto y el mismo target. Distribución: Mac App Store y App Store. Hoy solo existe la prueba de Inicio con datos falsos (plan `specs/plans/2026-10-08-apple-home-spike.md`).

## Alcance

El mismo que Android: todo lo definido en `product/`. Se construye por rebanadas después de la versión de Android (ADR 0001).

## Requisitos específicos (borrador, a confirmar en la primera rebanada real)

- **MAC-001** Debe funcionar en una ventana redimensionable, con el contenido legible desde 480 pt de ancho.
- **MAC-002** Debe ser compatible con VoiceOver y usable con teclado.
- **MAC-003** Debe abrir EPUB desde el Finder ("Abrir con" y arrastrar a la app o al Dock) (LIB-001).
- **MAC-004** Debe cumplir las reglas de la Mac App Store (sandbox, eliminación de cuenta, CMP-001).

Requisitos de iOS (borrador, plan `specs/plans/2026-10-09-ios-layout.md`):

- **IOS-001** Debe verse completa en un iPhone en vertical desde 375 pt de ancho: sin contenido cortado ni scroll horizontal de la pantalla, respetando las áreas seguras (isla, barra de estado, indicador de inicio).
- **IOS-002** La barra de estado debe leerse sobre el fondo de la app.

## Arquitectura

Stack y capas en `specs/adr/0010-apple-stack-and-architecture.md` (provisional): SwiftUI, MVVM en capas con el dominio en un paquete Swift local, proyecto generado con XcodeGen, Swift Testing.

## Decisiones

- Versión mínima: macOS 14 e iOS 17. Un solo target con los dos destinos.
- Interfaz con Host Grotesk y la paleta del diseño, como Android.
- Inicio (prueba, K-076): mismo contenido que en Android, centrado con un ancho máximo de 640 pt. Sin barra inferior: la navegación de la Mac (probablemente barra lateral) espera un diseño.

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en una Mac real, y la app aprobada en la Mac App Store.
