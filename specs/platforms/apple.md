# Plataforma — Apple: Mac e iOS (borrador)

Mac primero, iOS después, desde el mismo proyecto y el mismo target. Distribución: Mac App Store y App Store. Hoy existen Inicio y la biblioteca con los libros reales de la cuenta (planes `specs/plans/2026-10-09-ios-home-data.md` y `2026-10-09-ios-library.md`), sin lector ni importación.

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
- Barra inferior (HOM-005, K-096): en iPhone, la misma que Android (píldora flotante, el contenido pasa por debajo). Buscar y Perfil muestran el aviso de HOM-006. En la Mac no hay barra: su navegación (probablemente barra lateral) espera un diseño (K-078).
- Biblioteca (K-119 a K-122): la de Android según el diseño 05 (pestañas Todos, Leyendo y Terminados, grilla con progreso y "En la nube"). En iPhone es el destino Estantes; en la Mac se abre desde "Ver todo" de Inicio, con volver. "Ver todo" abre la biblioteca con el filtro de la sección (HOM-011). "Importar" muestra el aviso de HOM-006 hasta K-079 y no aparece en la Mac. Sin estado de sincronización: en Apple solo se baja de la nube, una vez al arrancar, desde la raíz de la app.

## Criterio de terminado

Todos los requisitos "debe" de `product/` cumplidos y verificados en una Mac real, y la app aprobada en la Mac App Store.
