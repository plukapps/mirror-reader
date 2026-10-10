# ADR 0013 — Motor de EPUB en las apps de Apple: Readium Swift Toolkit

**Estado:** provisional. Concreta para Apple el ADR 0004 (Readium) y lo que el ADR 0010 dejó pendiente. Plan: `specs/plans/2026-10-09-ios-reader.md`.

## Contexto

El lector de Android usa Readium Kotlin Toolkit 3.4.0. El ADR 0004 elige Readium también para Apple, y las ubicaciones de lectura tienen que ser compatibles entre plataformas (ADR 0011). Hace falta fijar versión, cómo se integra al proyecto de Mac e iOS y qué pasa en la Mac.

## Decisión

- **Readium Swift Toolkit 3.9.0**, por Swift Package Manager en `project.yml`, productos `ReadiumShared`, `ReadiumStreamer` y `ReadiumNavigator`.
- **Versión:** 3.10 y 3.11 dependen de SQLite.swift 0.16, que exige Swift 6.1 (Xcode 16.3). Esta máquina tiene Xcode 16.2 (K-080). 3.9.0 es la última que resuelve y compila. Se sube cuando se actualice Xcode.
- **Solo iOS:** Readium Swift declara solo iOS. En el target único (ADR 0010) los productos se enlazan con `destinationFilters: [iOS]` y el código que los usa va con `#if os(iOS)`. En la Mac no hay lector ni importación hasta que haya un motor para ella (K-079).
- **Integración:** `EPUBNavigatorViewController` envuelto en SwiftUI con `UIViewControllerRepresentable`, como indica la guía oficial (`docs/Guides/Navigator/SwiftUI.md`). Sin servidor HTTP: desde la 3.0 el navegador no lo necesita.
- **Concurrencia:** Readium compila en modo Swift 5; la app (Swift 6) lo importa con `@preconcurrency import` y toca el navegador solo desde el hilo principal.
- **Tipografías:** las tres del ADR 0009 viajan en `App/Fonts` y se declaran con `fontFamilyDeclarations` (`CSSFontFamilyDeclaration`, `CSSFontFace` con peso variable), con los mismos nombres de familia que Android.
- **Ubicaciones:** `Locator` serializado a JSON, el mismo formato que escribe Android (ADR 0011).

## Alternativas consideradas

- **Readium 3.11 con Xcode 16.3 o posterior.** Exige actualizar macOS en esta máquina (K-080). Se hará antes de publicar.
- **Motor propio sobre `WKWebView`.** Descartado por el ADR 0004: paginar, ubicar y seleccionar es justo lo que Readium resuelve.
- **Un target aparte para iOS.** Descartado por el ADR 0010; el filtro por destino alcanza.

## Consecuencias

- La Mac compila sin Readium y no abre libros todavía.
- El primer build descarga Readium y sus dependencias (SwiftSoup, CryptoSwift, ZIPFoundation, Fuzi, GCDWebServer, DifferenceKit).
- Al subir de versión de Readium hay que revisar los cambios de API del navegador y de las preferencias.
