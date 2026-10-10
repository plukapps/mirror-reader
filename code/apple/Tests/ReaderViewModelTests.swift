import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Mismas reglas que `ReaderViewModelTest` de Android, sin la animación ni la nube (plan `2026-10-09-ios-reader.md`).

@MainActor
private final class FakeSource: ReaderBookSource {
    var book: OpenedBook?
    var error: Error?
    private(set) var opened: [String] = []

    init(book: OpenedBook? = nil, error: Error? = nil) {
        self.book = book
        self.error = error
    }

    func open(bookId: String) async throws -> OpenedBook {
        opened.append(bookId)
        if let error { throw error }
        return book!
    }
}

private actor FakePositions: ReadingPositionRepository {
    private(set) var saved: [SavedPosition] = []
    func position(bookId: String) async -> SavedPosition? { saved.last }
    func save(bookId: String, position: SavedPosition) async { saved.append(position) }
}

private final class FakeSettings: ReaderSettingsRepository, @unchecked Sendable {
    var stored = ReaderSettings()
    func load() -> ReaderSettings { stored }
    func save(_ settings: ReaderSettings) { stored = settings }
}

private let book = OpenedBook(
    title: "Walden",
    toc: [
        TocItem(title: "Capítulo 1", href: "c1.xhtml", depth: 0),
        TocItem(title: "La mañana", href: "c1.xhtml#manana", depth: 1),
        TocItem(title: "Notas", href: "notes.xhtml", depth: 0),
    ],
    readingOrder: ["cover.xhtml", "c1.xhtml", "notes.xhtml"],
    initialLocatorJson: "{\"href\":\"c1.xhtml\"}"
)

@MainActor
struct ReaderViewModelTests {
    private let positions = FakePositions()
    private let settings = FakeSettings()

    private func makeViewModel(_ source: FakeSource, debounce: Duration = .zero) -> ReaderViewModel {
        ReaderViewModel(bookId: "a", source: source, positions: positions, settings: settings, saveDelay: debounce)
    }

    @Test("RDR-007: abre el libro pedido y queda listo, con los controles visibles")
    func loadsBook() async {
        let source = FakeSource(book: book)
        let viewModel = makeViewModel(source)
        #expect(viewModel.phase == .loading)

        await viewModel.load()

        #expect(source.opened == ["a"])
        #expect(viewModel.phase == .ready(book))
        #expect(viewModel.controlsVisible)
    }

    @Test("LIB-002: si no se puede abrir, muestra el motivo")
    func failureShowsMessage() async {
        let viewModel = makeViewModel(FakeSource(error: BookOpenError("Sin conexión. No se pudo descargar el libro.")))
        await viewModel.load()
        #expect(viewModel.phase == .failed("Sin conexión. No se pudo descargar el libro."))
    }

    @Test("Un error inesperado da un mensaje genérico")
    func unexpectedFailureIsGeneric() async {
        let viewModel = makeViewModel(FakeSource(error: CocoaError(.fileReadUnknown)))
        await viewModel.load()
        #expect(viewModel.phase == .failed("No se pudo abrir el libro."))
    }

    @Test("RDR-005, RDR-010, RDR-013: cada posición actualiza progreso, número de página y capítulo")
    func locationUpdatesFooterAndChapter() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()

        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.426, position: 12, href: "c1.xhtml#manana")

        #expect(viewModel.progressPercent == 43)
        #expect(viewModel.pageLabel == "12")
        #expect(viewModel.chapterTitle == "La mañana")
    }

    @Test("RDR-006: guarda la última posición después de la espera, no cada una")
    func savesLastPositionAfterDelay() async throws {
        let viewModel = makeViewModel(FakeSource(book: book), debounce: .milliseconds(50))
        await viewModel.load()

        viewModel.onLocationChanged(locatorJson: "uno", totalProgression: 0.1, position: 1, href: "c1.xhtml")
        viewModel.onLocationChanged(locatorJson: "dos", totalProgression: 0.2, position: 2, href: "c1.xhtml")
        try await Task.sleep(for: .milliseconds(300))

        #expect(await positions.saved == [SavedPosition(locatorJson: "dos", progress: 0.2)])
    }

    @Test("RDR-006: al cerrar guarda la posición pendiente sin esperar")
    func closeFlushesPendingPosition() async {
        let viewModel = makeViewModel(FakeSource(book: book), debounce: .seconds(60))
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "tres", totalProgression: 0.3, position: 3, href: "c1.xhtml")

        await viewModel.close()

        #expect(await positions.saved == [SavedPosition(locatorJson: "tres", progress: 0.3)])
    }

    @Test("RDR-012: al pasar del cuerpo a las notas avisa una sola vez")
    func announcesEndOfBodyOnce() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()

        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.5, position: 5, href: "c1.xhtml")
        #expect(viewModel.notice == nil)
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.9, position: 9, href: "notes.xhtml")
        #expect(viewModel.notice == "Parece que terminaste el libro")

        viewModel.dismissNotice()
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.5, position: 5, href: "c1.xhtml")
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.9, position: 9, href: "notes.xhtml")
        #expect(viewModel.notice == nil)
    }

    @Test("RDR-011: tocar el centro muestra u oculta los controles")
    func togglesControls() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()
        viewModel.toggleControls()
        #expect(!viewModel.controlsVisible)
        viewModel.toggleControls()
        #expect(viewModel.controlsVisible)
    }

    @Test("RDR-013, RDR-014: \"Aa\" abre y cierra el panel; abrirlo muestra los controles")
    func settingsPanelToggles() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()
        viewModel.toggleControls()

        viewModel.toggleSettings()
        #expect(viewModel.settingsOpen)
        #expect(viewModel.controlsVisible)

        viewModel.toggleSettings()
        #expect(!viewModel.settingsOpen)
    }

    @Test("RDR-014: cada ajuste se aplica y se guarda como preferencia")
    func settingsAreAppliedAndSaved() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()

        viewModel.setTheme(.sepia)
        viewModel.setFont(.mono)
        viewModel.setLineSpacing(.wide)
        viewModel.biggerFont()

        let expected = ReaderSettings(theme: .sepia, fontScale: 1.1, font: .mono, lineSpacing: .wide)
        #expect(viewModel.settings == expected)
        #expect(settings.stored == expected)
    }

    @Test("RDR-014: abrir el índice desde el panel cierra el panel; elegir un capítulo pide el salto y cierra el índice")
    func tableOfContentsFlow() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()
        viewModel.toggleSettings()

        viewModel.openTableOfContents()
        #expect(!viewModel.settingsOpen)
        #expect(viewModel.tocOpen)

        viewModel.select(book.toc[2])
        #expect(!viewModel.tocOpen)
        #expect(viewModel.jump?.href == "notes.xhtml")
    }
}
