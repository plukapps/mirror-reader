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
/// pulsación larga selecciona texto y no pasa de página (lo resuelve Readium). Sobre la selección se muestra un menú
/// propio con Nota, Copiar y Compartir (ANN-008, diseño 06) en lugar del del sistema: Readium 3.9 agrega las acciones
/// propias con `UIMenuController`, que iOS 26 ya no muestra (K-332). Las notas se ven subrayadas (guía
/// `Navigator/Highlights.md` de Readium 3.9.0).
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
        context.coordinator.attach(to: navigator, initial: initial)
        return navigator
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {
        context.coordinator.update(
            settings: viewModel.settings, jump: viewModel.jump, notes: viewModel.bookNotes,
            clearSelection: viewModel.clearSelectionRequest
        )
    }

    /// Amarillo del subrayado, el del diseño (pantalla 06). Readium le baja la opacidad al dibujarlo.
    static let noteTint = UIColor(red: 0xFB / 255, green: 0xD2 / 255, blue: 0x56 / 255, alpha: 1)
    /// Grupo de decoraciones de las notas (ANN-008).
    static let notesGroup = "notes"

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
        private var appliedNotes: [Note] = []
        private var appliedClearSelection = 0
        /// K-332: mientras se muestra el menú propio, se mira si la selección sigue (tocar afuera la quita).
        private var watchingSelection: Task<Void, Never>?
        private var edgeTaps: DirectionalNavigationAdapter?
        private var tapToken: InputObservableToken?
        /// SYN-014: texto que ancla la posición actual; se reemplaza cuando sale de la página visible.
        private var keptText: Locator.Text?
        private var anchoring: Task<Void, Never>?

        init(viewModel: ReaderViewModel) {
            self.viewModel = viewModel
            appliedSettings = viewModel.settings
            appliedJump = viewModel.jump
        }

        func attach(to navigator: EPUBNavigatorViewController, initial: Locator?) {
            self.navigator = navigator
            keptText = initial?.text.highlight != nil ? initial?.text : nil
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
            // ANN-009: tocar un subrayado abre su nota.
            navigator.observeDecorationInteractions(inGroup: ReaderNavigator.notesGroup) { [weak self] event in
                guard let self else { return }
                Task { await self.viewModel.openNote(id: event.decoration.id) }
            }
            apply(notes: viewModel.bookNotes)
        }

        /// K-332: la selección se queda con el menú propio, no con el del sistema. El menú se va cuando la selección
        /// desaparece (tocar afuera, pasar de página) o cuando el usuario elige una acción.
        private func show(_ selection: Selection) {
            let locator = selection.locator
            viewModel.selectionChanged(
                ReaderViewModel.TextSelection(
                    locatorJson: (try? locator.jsonString()) ?? "{}",
                    quote: locator.text.highlight ?? "",
                    progression: locator.locations.totalProgression,
                    frame: selection.frame ?? .zero
                )
            )
            watchingSelection?.cancel()
            watchingSelection = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(300))
                    guard let self else { return }
                    guard self.navigator?.currentSelection == nil else { continue }
                    self.viewModel.selectionChanged(nil)
                    return
                }
            }
        }

        /// ANN-008: las notas se dibujan subrayadas. Readium compara con la lista anterior y aplica solo lo que cambió.
        private func apply(notes: [Note]) {
            appliedNotes = notes
            let decorations = notes.compactMap { note -> Decoration? in
                // Un lugar dañado no se dibuja; la nota sigue en el detalle del libro con su texto citado (ANN-006).
                guard let locator = try? Locator(jsonString: note.locatorJson) else { return nil }
                return Decoration(id: note.id, locator: locator, style: .highlight(tint: ReaderNavigator.noteTint))
            }
            navigator?.apply(decorations: decorations, in: ReaderNavigator.notesGroup)
        }

        func update(settings: ReaderSettings, jump: ReaderViewModel.Jump?, notes: [Note], clearSelection: Int) {
            guard let navigator else { return }
            if notes != appliedNotes { apply(notes: notes) }
            if clearSelection != appliedClearSelection {
                appliedClearSelection = clearSelection
                watchingSelection?.cancel()
                navigator.clearSelection()
            }
            if settings != appliedSettings {
                appliedSettings = settings
                navigator.submitPreferences(EPUBPreferences(settings))
            }
            if let jump, jump != appliedJump {
                appliedJump = jump
                // SYN-013: la posición de otro dispositivo llega como locator; un capítulo del índice, como enlace.
                if let json = jump.locatorJson, let locator = try? Locator(jsonString: json) {
                    keptText = locator.text.highlight != nil ? locator.text : nil
                    Task { await navigator.go(to: locator) }
                } else {
                    Task { await navigator.go(to: Link(href: jump.href)) }
                }
            }
        }

        // MARK: EPUBNavigatorDelegate

        /// ANN-008, K-332: en lugar del menú del sistema, el menú propio con Nota, Copiar y Compartir.
        func navigator(_ navigator: SelectableNavigator, shouldShowMenuForSelection selection: Selection) -> Bool {
            show(selection)
            return false
        }

        /// SYN-014: el locator guarda además el texto del comienzo de la página. El de la posición desde la que se abrió
        /// se conserva mientras siga a la vista.
        func navigator(_ navigator: Navigator, locationDidChange locator: Locator) {
            anchoring?.cancel()
            anchoring = Task { [weak self, weak navigator = navigator as? EPUBNavigatorViewController] in
                guard let self else { return }
                let visible = await navigator?.firstVisibleText(kept: self.keptText) ?? .none
                guard !Task.isCancelled else { return }
                switch visible {
                case .kept: break
                case let .found(anchor): self.keptText = anchor.locatorText
                case .none: self.keptText = nil
                }
                var anchored = locator
                if let keptText = self.keptText { anchored.text = keptText }
                self.viewModel.onLocationChanged(
                    locatorJson: (try? anchored.jsonString()) ?? "{}",
                    totalProgression: locator.locations.totalProgression,
                    position: locator.locations.position,
                    href: locator.href.string
                )
            }
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
