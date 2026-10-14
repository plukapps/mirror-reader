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

/// Notas en memoria, con el mismo orden que la base (ANN-010).
private actor FakeNotes: NoteRepository {
    private(set) var all: [Note] = []
    init(_ notes: [Note] = []) { all = notes }
    func notes(bookId: String) async -> [Note] {
        all.filter { $0.bookId == bookId }.sorted { ($0.progression, $0.createdAt) < ($1.progression, $1.createdAt) }
    }
    func note(id: String) async -> Note? { all.first { $0.id == id } }
    func save(_ note: Note) async { all.removeAll { $0.id == note.id }; all.append(note) }
    func delete(id: String) async { all.removeAll { $0.id == id } }
}

private func note(_ id: String, bookId: String = "a", progression: Double = 0.5) -> Note {
    Note(
        id: id, bookId: bookId, locatorJson: #"{"href":"c1.xhtml","id":"\#(id)"}"#, quote: "texto citado \(id)",
        text: "nota \(id)", progression: progression, createdAt: 1, updatedAt: 1
    )
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

    private func makeViewModel(_ source: FakeSource, debounce: Duration = .zero, plan: PlanId = .free) -> ReaderViewModel {
        ReaderViewModel(bookId: "a", source: source, positions: positions, settings: settings, saveDelay: debounce, plan: { plan })
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

    @Test("RDR-014: con Lector, cada ajuste se aplica y se guarda como preferencia")
    func settingsAreAppliedAndSaved() async {
        let viewModel = makeViewModel(FakeSource(book: book), plan: .reader)
        await viewModel.load()

        viewModel.setTheme(.sepia)
        viewModel.setFont(.mono)
        viewModel.setLineSpacing(.wide)
        viewModel.biggerFont()

        let expected = ReaderSettings(theme: .sepia, fontScale: 1.1, font: .mono, lineSpacing: .wide)
        #expect(viewModel.settings == expected)
        #expect(settings.stored == expected)
    }

    @Test("PLN-015: en Gratis, tocar Sepia en el panel no cambia el tema y abre la hoja al cerrarse el panel")
    func freePlanThemeOpensPaidSheet() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()
        viewModel.toggleSettings()
        viewModel.setTheme(.sepia)
        #expect(viewModel.settings.theme == .light)
        #expect(settings.stored.theme == .light)
        #expect(!viewModel.settingsOpen)
        #expect(viewModel.paidFeature == nil)
        viewModel.settingsDismissed()
        #expect(viewModel.paidFeature == .themesAndFonts)
        viewModel.dismissPaidFeature()
        viewModel.paidFeatureDismissed()
        #expect(!viewModel.plansOpen)
        #expect(viewModel.isLocked(ReaderFont.mono))
        #expect(!viewModel.isLocked(ReadingTheme.light))
    }

    @Test("PLN-015: en Gratis la letra paga pide la hoja; tamaño e interlineado siguen libres; Ver planes abre P01")
    func freePlanFontAndSeePlans() async {
        let viewModel = makeViewModel(FakeSource(book: book))
        await viewModel.load()
        viewModel.setFont(.mono)
        #expect(viewModel.paidFeature == .themesAndFonts)
        #expect(viewModel.settings.font == .serif)
        viewModel.seePlans()
        #expect(viewModel.paidFeature == nil)
        viewModel.paidFeatureDismissed()
        #expect(viewModel.plansOpen)
        viewModel.biggerFont()
        viewModel.setLineSpacing(.wide)
        #expect(settings.stored == ReaderSettings(theme: .light, fontScale: 1.1, font: .serif, lineSpacing: .wide))
    }

    @Test("PLN-015: en Gratis lo guardado de un plan pago no se borra; el lector usa Clásico y serifa")
    func freePlanKeepsSavedPreference() async {
        settings.stored = ReaderSettings(theme: .dark, fontScale: 1.2, font: .mono, lineSpacing: .wide)
        let free = makeViewModel(FakeSource(book: book))
        #expect(free.settings == ReaderSettings(theme: .light, fontScale: 1.2, font: .serif, lineSpacing: .wide))
        free.biggerFont()
        #expect(settings.stored.theme == .dark)
        #expect(settings.stored.font == .mono)
        #expect(makeViewModel(FakeSource(book: book), plan: .library).settings.theme == .dark)
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

    // MARK: Notas (ANN-008 a ANN-010)

    private func makeViewModel(notes: FakeNotes, noteId: String? = nil, source: FakeSource = FakeSource(book: book)) -> ReaderViewModel {
        ReaderViewModel(
            bookId: "a", source: source, positions: positions, settings: settings, saveDelay: .zero,
            notes: notes, noteId: noteId, clock: { Date(timeIntervalSince1970: 50) }
        )
    }

    @Test("ANN-008: elegir Nota sobre una selección abre el editor con el texto citado; guardar crea la nota subrayada")
    func savesANoteOnTheSelection() async {
        let notes = FakeNotes()
        let viewModel = makeViewModel(notes: notes)
        await viewModel.load()
        viewModel.onTextSelected(locatorJson: #"{"href":"c2.xhtml"}"#, quote: "  Empieza la mañana  ", progression: 0.42)
        #expect(viewModel.noteEditor == .init(noteId: nil, quote: "Empieza la mañana", text: "", locatorJson: #"{"href":"c2.xhtml"}"#, progression: 0.42))
        await viewModel.saveNote(" Para el lunes ")
        #expect(viewModel.noteEditor == nil)
        let saved = await notes.all
        #expect(saved.count == 1)
        #expect(saved[0].bookId == "a")
        #expect(saved[0].quote == "Empieza la mañana")
        #expect(saved[0].text == "Para el lunes")
        #expect(saved[0].progression == 0.42)
        #expect(saved[0].createdAt == 50_000)
        #expect(viewModel.bookNotes.map(\.id) == [saved[0].id])
    }

    @Test("ANN-008: cancelar no guarda nada; un texto vacío no se guarda; una selección vacía no abre el editor")
    func cancelAndBlank() async {
        let notes = FakeNotes()
        let viewModel = makeViewModel(notes: notes)
        await viewModel.load()
        viewModel.onTextSelected(locatorJson: "{}", quote: "   ", progression: 0.1)
        #expect(viewModel.noteEditor == nil)
        viewModel.onTextSelected(locatorJson: "{}", quote: "frase", progression: 0.1)
        await viewModel.saveNote("   ")
        #expect(viewModel.noteEditor != nil)
        viewModel.closeNote()
        #expect(viewModel.noteEditor == nil)
        #expect(await notes.all.isEmpty)
    }

    @Test("ANN-009: tocar un subrayado muestra la nota; editarla conserva el id, el lugar y la fecha de creación")
    func editsFromTheHighlight() async {
        let notes = FakeNotes([note("n1")])
        let viewModel = makeViewModel(notes: notes)
        await viewModel.load()
        #expect(viewModel.bookNotes.map(\.id) == ["n1"])
        await viewModel.openNote(id: "n1")
        #expect(viewModel.noteEditor?.noteId == "n1")
        #expect(viewModel.noteEditor?.text == "nota n1")
        await viewModel.saveNote("corregida")
        let edited = await notes.note(id: "n1")
        #expect(edited?.text == "corregida")
        #expect(edited?.locatorJson == note("n1").locatorJson)
        #expect(edited?.createdAt == 1)
        #expect(edited?.updatedAt == 50_000)
    }

    @Test("ANN-009: borrar desde el lector quita la nota y su subrayado")
    func deletesFromTheHighlight() async {
        let notes = FakeNotes([note("n1"), note("n2", progression: 0.9), note("ajena", bookId: "otro")])
        let viewModel = makeViewModel(notes: notes)
        await viewModel.load()
        #expect(viewModel.bookNotes.map(\.id) == ["n1", "n2"])
        await viewModel.openNote(id: "n1")
        await viewModel.deleteNote()
        #expect(viewModel.noteEditor == nil)
        #expect(viewModel.bookNotes.map(\.id) == ["n2"])
    }

    @Test("ANN-010: abrir desde una nota abre en su lugar, no en la última posición")
    func opensAtTheNote() async {
        let viewModel = makeViewModel(notes: FakeNotes([note("n1")]), noteId: "n1")
        await viewModel.load()
        guard case let .ready(opened) = viewModel.phase else { Issue.record("no abrió"); return }
        #expect(opened.initialLocatorJson == note("n1").locatorJson)
    }

    @Test("ANN-010: una nota que ya no existe o de otro libro abre en la última posición")
    func missingOrForeignNote() async {
        for noteId in ["borrada", "ajena"] {
            let viewModel = makeViewModel(notes: FakeNotes([note("ajena", bookId: "otro")]), noteId: noteId)
            await viewModel.load()
            guard case let .ready(opened) = viewModel.phase else { Issue.record("no abrió"); return }
            #expect(opened.initialLocatorJson == book.initialLocatorJson)
        }
    }
}
