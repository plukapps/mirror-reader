import Foundation
import Observation
import ReaderDomain

/// Entrada del índice ya aplanada, con su nivel para la sangría (RDR-004).
struct TocItem: Identifiable, Equatable, Sendable {
    let title: String
    let href: String
    let depth: Int
    var id: String { "\(depth)|\(href)|\(title)" }
}

/// Publicación abierta por el motor de EPUB. La UI del lector se la pasa al navegador sin mirarla, así el
/// `ReaderViewModel` no depende de Readium (ADR 0013) y se testea con libros falsos.
@MainActor
final class OpenedPublication {
    let value: AnyObject
    private let onClose: @MainActor () -> Void

    init(_ value: AnyObject, onClose: @escaping @MainActor () -> Void) {
        self.value = value
        self.onClose = onClose
    }

    func close() { onClose() }
}

/// Lo que el lector necesita de un libro abierto.
struct OpenedBook: Equatable, Sendable {
    let title: String
    let toc: [TocItem]
    /// Recursos del libro en orden de lectura, como los nombra el locator.
    let readingOrder: [String]
    /// Última posición guardada (RDR-006), o nil para empezar al principio.
    let initialLocatorJson: String?
    var publication: OpenedPublication?

    static func == (lhs: OpenedBook, rhs: OpenedBook) -> Bool {
        lhs.title == rhs.title && lhs.toc == rhs.toc && lhs.readingOrder == rhs.readingOrder
            && lhs.initialLocatorJson == rhs.initialLocatorJson && lhs.publication === rhs.publication
    }
}

/// Abre un libro de la biblioteca (bajándolo antes si está solo en la nube). Lanza `BookOpenError`.
@MainActor
protocol ReaderBookSource: AnyObject {
    func open(bookId: String) async throws -> OpenedBook
}

/// Estado del lector, como `ReaderViewModel` de Android sin la animación de página ni la nube
/// (plan `2026-10-09-ios-reader.md`).
@MainActor
@Observable
final class ReaderViewModel {
    enum Phase: Equatable {
        case loading
        case failed(String)
        case ready(OpenedBook)
    }

    /// Pedido de salto a un capítulo del índice. `id` cambia en cada pedido, aunque sea el mismo capítulo.
    struct Jump: Equatable {
        let href: String
        let id: Int
    }

    private(set) var phase: Phase = .loading
    private(set) var settings: ReaderSettings
    /// RDR-013: vacío si el libro no tiene índice o la posición está antes del primer capítulo.
    private(set) var chapterTitle = ""
    /// RDR-010: posición del libro, sin total.
    private(set) var pageLabel: String?
    private(set) var progressPercent: Int?
    private(set) var controlsVisible = true
    private(set) var settingsOpen = false
    var tocOpen = false
    /// Aviso breve sobre el libro (RDR-012).
    private(set) var notice: String?
    private(set) var jump: Jump?

    private let bookId: String
    private let source: ReaderBookSource
    private let positions: ReadingPositionRepository
    private let settingsRepository: ReaderSettingsRepository
    private let saveDelay: Duration
    private var chapters: [ChapterEntry] = []
    private var readingOrder: [String] = []
    private var bodyEnd: BodyEndDetector?
    private var pendingPosition: SavedPosition?
    private var saveTask: Task<Void, Never>?

    init(
        bookId: String,
        source: ReaderBookSource,
        positions: ReadingPositionRepository,
        settings: ReaderSettingsRepository,
        saveDelay: Duration = .milliseconds(250)
    ) {
        self.bookId = bookId
        self.source = source
        self.positions = positions
        settingsRepository = settings
        self.saveDelay = saveDelay
        self.settings = settings.load()
    }

    func load() async {
        guard phase == .loading else { return }
        do {
            let book = try await source.open(bookId: bookId)
            chapters = book.toc.map { ChapterEntry(title: $0.title, href: $0.href) }
            readingOrder = book.readingOrder
            let topLevel = book.toc.filter { $0.depth == 0 }.map { BodyEntry(title: $0.title, href: $0.href) }
            bodyEnd = BodyEndDetector(readingOrder: readingOrder, backMatterStart: backMatterStart(toc: topLevel, readingOrder: readingOrder))
            phase = .ready(book)
        } catch let error as BookOpenError {
            phase = .failed(error.message)
        } catch {
            phase = .failed("No se pudo abrir el libro.")
        }
    }

    /// Cada cambio de posición del navegador (RDR-005, RDR-006, RDR-010, RDR-012, RDR-013).
    func onLocationChanged(locatorJson: String, totalProgression: Double?, position: Int?, href: String) {
        progressPercent = ReaderDomain.progressPercent(totalProgression)
        pageLabel = ReaderDomain.pageLabel(position: position)
        chapterTitle = currentChapterTitle(toc: chapters, readingOrder: readingOrder, href: href)
        if bodyEnd?.onResource(href) == true {
            notice = "Parece que terminaste el libro"
        }
        scheduleSave(SavedPosition(locatorJson: locatorJson, progress: totalProgression))
    }

    /// Al salir del lector: guarda la posición pendiente y libera el libro.
    func close() async {
        saveTask?.cancel()
        saveTask = nil
        await flushPosition()
        if case let .ready(book) = phase { book.publication?.close() }
    }

    func dismissNotice() { notice = nil }

    // MARK: Controles (RDR-011, RDR-013, RDR-014)

    func toggleControls() {
        if settingsOpen {
            settingsOpen = false
            return
        }
        controlsVisible.toggle()
    }

    func toggleSettings() {
        settingsOpen.toggle()
        if settingsOpen { controlsVisible = true }
    }

    func closeSettings() { settingsOpen = false }

    func openTableOfContents() {
        settingsOpen = false
        tocOpen = true
    }

    func select(_ item: TocItem) {
        tocOpen = false
        jump = Jump(href: item.href, id: (jump?.id ?? 0) + 1)
    }

    // MARK: Ajustes (RDR-002, RDR-003, RDR-014)

    func setTheme(_ theme: ReadingTheme) { update(settings.withTheme(theme)) }
    func setFont(_ font: ReaderFont) { update(settings.withFont(font)) }
    func setLineSpacing(_ spacing: LineSpacing) { update(settings.withLineSpacing(spacing)) }
    func biggerFont() { update(settings.biggerFont()) }
    func smallerFont() { update(settings.smallerFont()) }

    private func update(_ newSettings: ReaderSettings) {
        guard newSettings != settings else { return }
        settings = newSettings
        settingsRepository.save(newSettings)
    }

    // MARK: Posición (RDR-006)

    private func scheduleSave(_ position: SavedPosition) {
        pendingPosition = position
        saveTask?.cancel()
        let delay = saveDelay
        saveTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            await self?.flushPosition()
        }
    }

    private func flushPosition() async {
        guard let position = pendingPosition else { return }
        pendingPosition = nil
        await positions.save(bookId: bookId, position: position)
    }
}
