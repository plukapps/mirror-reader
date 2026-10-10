#if os(iOS)
@preconcurrency import ReadiumNavigator
@preconcurrency import ReadiumShared
import ReaderDomain
import SwiftUI
import UIKit

/// Ajustes de lectura traducidos al navegador de Readium, como `toEpubPreferences` de Android (RDR-001 a RDR-003,
/// RDR-014): siempre paginado, una columna, la letra y el interlineado del usuario por encima de los del libro.
extension EPUBPreferences {
    init(_ settings: ReaderSettings) {
        self.init(
            columnCount: .one,
            fontFamily: FontFamily(rawValue: settings.font.familyName),
            fontSize: settings.fontScale,
            lineHeight: settings.lineSpacing.lineHeight,
            pageMargins: ReaderNavigator.pageMargins,
            publisherStyles: false,
            scroll: false,
            spread: .never,
            theme: Theme(settings.theme)
        )
    }
}

private extension Theme {
    init(_ theme: ReadingTheme) {
        switch theme {
        case .light: self = .light
        case .sepia: self = .sepia
        case .dark: self = .dark
        }
    }
}

/// Navegador de EPUB de Readium dentro de SwiftUI (guía `Navigator/SwiftUI.md` de Readium, ADR 0013).
///
/// Toques (RDR-011): el 20 % izquierdo y derecho pasan de página, el resto muestra u oculta los controles. Una
/// pulsación larga selecciona texto y no pasa de página (lo resuelve Readium).
struct ReaderNavigator: UIViewControllerRepresentable {
    let publication: Publication
    let initialLocatorJson: String?
    let viewModel: ReaderViewModel

    /// Margen lateral de Readium. Con 1.0 queda cerca de los 24 pt del diseño en un iPhone.
    nonisolated static let pageMargins = 1.0
    /// Fracción del ancho que pasa de página en cada borde (RDR-011).
    static let edgeFraction = 0.2
    /// Espacio arriba del texto para la barra superior y abajo para el número de página, además del área segura
    /// (RDR-010, RDR-013). Es fijo: mostrar u ocultar los controles no repagina el libro (RDR-015).
    static let topInset: CGFloat = 48
    static let bottomInset: CGFloat = 44

    func makeCoordinator() -> Coordinator { Coordinator(viewModel: viewModel) }

    func makeUIViewController(context: Context) -> UIViewController {
        let initial = initialLocatorJson.flatMap { try? Locator(jsonString: $0) }
        var config = EPUBNavigatorViewController.Configuration()
        config.preferences = EPUBPreferences(viewModel.settings)
        config.fontFamilyDeclarations = Self.fontFamilies
        guard let navigator = try? EPUBNavigatorViewController(publication: publication, initialLocation: initial, config: config) else {
            return UIViewController()
        }
        context.coordinator.attach(to: navigator)
        return navigator
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
        context.coordinator.update(settings: viewModel.settings, jump: viewModel.jump)
    }

    /// Las tres familias del ADR 0009, con los mismos nombres que en Android y que `ReaderFont.familyName`.
    private static var fontFamilies: [AnyHTMLFontFamilyDeclaration] {
        guard let resources = Bundle.main.resourceURL.flatMap(FileURL.init(url:)) else { return [] }
        func face(_ file: String, _ style: CSSFontStyle, _ weights: ClosedRange<Int>) -> CSSFontFace {
            CSSFontFace(file: resources.appendingPath(file, isDirectory: false), style: style, weight: .variable(weights))
        }
        return [
            CSSFontFamilyDeclaration(
                fontFamily: FontFamily(rawValue: ReaderFont.serif.familyName),
                fontFaces: [face("Newsreader.ttf", .normal, 200...800), face("Newsreader-Italic.ttf", .italic, 200...800)]
            ).eraseToAnyHTMLFontFamilyDeclaration(),
            CSSFontFamilyDeclaration(
                fontFamily: FontFamily(rawValue: ReaderFont.sans.familyName),
                fontFaces: [face("HostGrotesk.ttf", .normal, 300...800), face("HostGrotesk-Italic.ttf", .italic, 300...800)]
            ).eraseToAnyHTMLFontFamilyDeclaration(),
            CSSFontFamilyDeclaration(
                fontFamily: FontFamily(rawValue: ReaderFont.mono.familyName),
                fontFaces: [face("JetBrainsMono.ttf", .normal, 100...800)]
            ).eraseToAnyHTMLFontFamilyDeclaration(),
        ]
    }

    @MainActor
    final class Coordinator: NSObject, EPUBNavigatorDelegate {
        private let viewModel: ReaderViewModel
        private weak var navigator: EPUBNavigatorViewController?
        private var appliedSettings: ReaderSettings?
        private var appliedJump: ReaderViewModel.Jump?
        private var edgeTaps: DirectionalNavigationAdapter?
        private var tapToken: InputObservableToken?

        init(viewModel: ReaderViewModel) {
            self.viewModel = viewModel
            appliedSettings = viewModel.settings
            appliedJump = viewModel.jump
        }

        func attach(to navigator: EPUBNavigatorViewController) {
            self.navigator = navigator
            navigator.delegate = self
            let edgeTaps = DirectionalNavigationAdapter(
                pointerPolicy: .init(
                    types: [.touch, .mouse],
                    edges: .horizontal,
                    minimumHorizontalEdgeSize: 0,
                    horizontalEdgeThresholdPercent: ReaderNavigator.edgeFraction
                )
            )
            edgeTaps.bind(to: navigator)
            self.edgeTaps = edgeTaps
            // Después del adaptador: solo llegan acá los toques fuera de los bordes.
            tapToken = navigator.addObserver(.tap { [weak self] _ in
                self?.viewModel.toggleControls()
                return true
            })
        }

        func update(settings: ReaderSettings, jump: ReaderViewModel.Jump?) {
            guard let navigator else { return }
            if settings != appliedSettings {
                appliedSettings = settings
                navigator.submitPreferences(EPUBPreferences(settings))
            }
            if let jump, jump != appliedJump {
                appliedJump = jump
                // SYN-013: la posición de otro dispositivo llega como locator; un capítulo del índice, como enlace.
                if let json = jump.locatorJson, let locator = try? Locator(jsonString: json) {
                    Task { await navigator.go(to: locator) }
                } else {
                    Task { await navigator.go(to: Link(href: jump.href)) }
                }
            }
        }

        // MARK: EPUBNavigatorDelegate

        func navigator(_ navigator: Navigator, locationDidChange locator: Locator) {
            viewModel.onLocationChanged(
                locatorJson: (try? locator.jsonString()) ?? "{}",
                totalProgression: locator.locations.totalProgression,
                position: locator.locations.position,
                href: locator.href.string
            )
        }

        func navigatorContentInset(_ navigator: VisualNavigator) -> UIEdgeInsets? {
            let safe = (navigator as? UIViewController)?.view.window?.safeAreaInsets ?? .zero
            return UIEdgeInsets(top: safe.top + ReaderNavigator.topInset, left: 0, bottom: safe.bottom + ReaderNavigator.bottomInset, right: 0)
        }

        func navigator(_ navigator: Navigator, presentError error: NavigatorError) {}

        /// Solo se abren enlaces web, como en Android.
        func navigator(_ navigator: Navigator, presentExternalURL url: URL) {
            guard ["http", "https"].contains(url.scheme?.lowercased()) else { return }
            UIApplication.shared.open(url)
        }
    }
}
#endif
