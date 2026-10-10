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

/// La posición de la nube vista desde el lector (SYN-003, SYN-011, SYN-013). La implementación une
/// `ResolveOpeningPosition` y `PositionSync`; los tests usan una falsa.
protocol ReaderPositionSync: Sendable {
    /// Consulta la posición más reciente del libro antes de mostrarlo (SYN-012).
    func resolve(bookId: String) async -> OpeningPosition
    func accept(_ remote: RemotePosition) async
    func decline(_ local: ReadingPosition) async
    /// Posiciones más recientes de otros dispositivos, mientras la app está a la vista.
    func remoteUpdates() async -> AsyncStream<RemotePosition>
    /// Envía lo pendiente sin esperar (SYN-011).
    func sendPending()
}

/// Estado del lector, como `ReaderViewModel` de Android sin la animación de página
/// (planes `2026-10-09-ios-reader.md` y `2026-10-10-ios-sync.md`).
@MainActor
@Observable
final class ReaderViewModel {
    enum Phase: Equatable {
        case loading
        case failed(String)
        /// SYN-003: otro dispositivo leyó más adelante y la diferencia es grande; se pregunta antes de abrir.
        case confirming(deviceName: String, remotePercent: Int?)
        case ready(OpenedBook)
    }

    /// Pedido de salto: a un capítulo del índice (`href`) o a la posición de otro dispositivo (`locatorJson`, SYN-013).
    /// `id` cambia en cada pedido, aunque sea el mismo destino.
    struct Jump: Equatable {
        let href: String
        var locatorJson: String? = nil
        let id: Int
    }

    /// SYN-013: lectura más adelantada de otro dispositivo que se ofrece seguir.
    struct ContinueFrom: Equatable {
        let deviceName: String
        let percent: Int?
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
    private(set) var continueFrom: ContinueFrom?

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
    private let sync: ReaderPositionSync?
    /// SYN-003: la elección pendiente mientras se pregunta.
    private var choice: (remote: RemotePosition, local: ReadingPosition)?
    private var remoteAhead: RemotePosition?
    private var currentProgression: Double?
    private var updatesTask: Task<Void, Never>?

    /// - Parameter sync: nil si la app funciona solo en local.
    init(
        bookId: String,
        source: ReaderBookSource,
        positions: ReadingPositionRepository,
        settings: ReaderSettingsRepository,
        saveDelay: Duration = .milliseconds(250),
        sync: ReaderPositionSync? = nil
    ) {
        self.bookId = bookId
        self.source = source
        self.positions = positions
        settingsRepository = settings
        self.saveDelay = saveDelay
        self.sync = sync
        self.settings = settings.load()
    }

    func load() async {
        guard phase == .loading, choice == nil else { return }
        // SYN-003, SYN-012: antes de mostrar el libro se consulta su posición más reciente en la nube.
        if let sync, case let .confirm(remote, local) = await sync.resolve(bookId: bookId) {
            choice = (remote, local)
            phase = .confirming(deviceName: remote.deviceName, remotePercent: ReaderDomain.progressPercent(remote.position.progress))
            return
        }
        await openBook()
    }

    /// SYN-003: el usuario eligió continuar desde el otro dispositivo (`useRemote`) o quedarse en la posición de este,
    /// que pasa a ser la más reciente.
    func resumeChoice(useRemote: Bool) async {
        guard let choice, let sync else { return }
        self.choice = nil
        phase = .loading
        if useRemote { await sync.accept(choice.remote) } else { await sync.decline(choice.local) }
        await openBook()
    }

    private func openBook() async {
        do {
            let book = try await source.open(bookId: bookId)
            chapters = book.toc.map { ChapterEntry(title: $0.title, href: $0.href) }
            readingOrder = book.readingOrder
            let topLevel = book.toc.filter { $0.depth == 0 }.map { BodyEntry(title: $0.title, href: $0.href) }
            bodyEnd = BodyEndDetector(readingOrder: readingOrder, backMatterStart: backMatterStart(toc: topLevel, readingOrder: readingOrder))
            phase = .ready(book)
            listenForOtherDevices()
        } catch let error as BookOpenError {
            phase = .failed(error.message)
        } catch {
            phase = .failed("No se pudo abrir el libro.")
        }
    }

    /// Cada cambio de posición del navegador (RDR-005, RDR-006, RDR-010, RDR-012, RDR-013).
    func onLocationChanged(locatorJson: String, totalProgression: Double?, position: Int?, href: String) {
        progressPercent = ReaderDomain.progressPercent(totalProgression)
        currentProgression = totalProgression
        // SYN-013: el aviso se retira solo cuando la lectura de aquí alcanza a la del otro dispositivo.
        if let remoteAhead, !shouldOfferJump(currentProgress: totalProgression, remote: remoteAhead) { offer(nil) }
        pageLabel = ReaderDomain.pageLabel(position: position)
        chapterTitle = currentChapterTitle(toc: chapters, readingOrder: readingOrder, href: href)
        if bodyEnd?.onResource(href) == true {
            notice = "Parece que terminaste el libro"
        }
        scheduleSave(SavedPosition(locatorJson: locatorJson, progress: totalProgression))
    }

    /// Al salir del lector: guarda la posición pendiente, la envía (SYN-011) y libera el libro.
    func close() async {
        updatesTask?.cancel()
        updatesTask = nil
        await saveNow()
        sync?.sendPending()
        if case let .ready(book) = phase { book.publication?.close() }
    }

    /// SYN-011: la app pasó a segundo plano con el libro abierto: guarda y envía, sin cerrarlo.
    func wentToBackground() async {
        await saveNow()
        sync?.sendPending()
    }

    // MARK: Otros dispositivos (SYN-013)

    /// El usuario aceptó seguir desde el otro dispositivo: la pantalla navega y la lectura nueva se guarda sola.
    func continueFromOtherDevice() {
        guard let remoteAhead else { return }
        offer(nil)
        jump = Jump(href: "", locatorJson: remoteAhead.position.locatorJson, id: (jump?.id ?? 0) + 1)
    }

    func dismissContinueFrom() { offer(nil) }

    private func listenForOtherDevices() {
        guard let sync, updatesTask == nil else { return }
        let bookId = bookId
        updatesTask = Task { [weak self] in
            for await remote in await sync.remoteUpdates() where remote.position.bookId == bookId {
                guard let self else { return }
                if shouldOfferJump(currentProgress: self.currentProgression, remote: remote) { self.offer(remote) }
            }
        }
    }

    private func offer(_ remote: RemotePosition?) {
        remoteAhead = remote
        continueFrom = remote.map { ContinueFrom(deviceName: $0.deviceName, percent: ReaderDomain.progressPercent($0.position.progress)) }
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

    private func saveNow() async {
        saveTask?.cancel()
        saveTask = nil
        await flushPosition()
    }

    private func flushPosition() async {
        guard let position = pendingPosition else { return }
        pendingPosition = nil
        await positions.save(bookId: bookId, position: position)
    }
}
