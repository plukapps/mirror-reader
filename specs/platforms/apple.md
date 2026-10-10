# Plataforma — Apple: Mac e iOS (borrador)

Mac primero, iOS después, desde el mismo proyecto y el mismo target. Distribución: Mac App Store y App Store. Hoy existen Inicio con los libros reales de la cuenta (plan `specs/plans/2026-10-09-ios-home-data.md`) y, en iPhone, la búsqueda local (plan `specs/plans/2026-10-09-ios-search.md`), sin lector ni importación.

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

Stack y capas en `specs/adr/0010-apple-stack-and-architecture.md` (provisional): SwiftUI, MVVM en capas con el dominio en un paquete Swift local, proyecto generado con XcodeGen, Swift Testing. Base local con SwiftData y SDK de Firebase para Apple: `specs/adr/0012-apple-persistence-and-firebase.md` (provisional).

## Decisiones

- Versión mínima: macOS 14 e iOS 17. Un solo target con los dos destinos.
- Interfaz con Host Grotesk y la paleta del diseño, como Android.
- Inicio con datos reales (K-107 a K-111): sesión con la cuenta de desarrollo (solo Debug), libros, posiciones y portadas bajados de Firebase a la base local al arrancar. Solo lectura: Apple todavía no sube nada.
- Inicio (prueba, K-076): mismo contenido que en Android, centrado con un ancho máximo de 640 pt.
- Barra inferior (HOM-005, K-096): en iPhone, la misma que Android (píldora flotante, el contenido pasa por debajo). Inicio y Buscar navegan; Estantes y Perfil muestran el aviso de HOM-006. En la Mac no hay barra: su navegación (probablemente barra lateral) espera un diseño (K-078), y hasta entonces la búsqueda no se alcanza desde la Mac.
- Búsqueda local (LIB-006, LIB-013 a LIB-015, K-119 a K-122): misma regla que Android (`searchBooks` en `ReaderDomain`, mismos casos de test) y la pantalla 03 del diseño. Tocar un resultado no abre nada hasta que exista el lector.

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en una Mac real, y la app aprobada en la Mac App Store.
