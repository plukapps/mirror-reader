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

    /// ANN-008, ANN-009: la nota que se está escribiendo o mirando. `noteId` es nil para una nota nueva sobre la
    /// selección. Como `NoteEditor` de Android.
    struct NoteEditor: Equatable, Identifiable {
        let noteId: String?
        let quote: String
        let text: String
        let locatorJson: String
        let progression: Double
        var id: String { noteId ?? "nueva|\(locatorJson)" }
    }

    /// ANN-008, K-332: el texto seleccionado en el libro y dónde está en la pantalla (coordenadas del navegador), para el
    /// menú propio de la selección.
    struct TextSelection: Equatable {
        let locatorJson: String
        let quote: String
        let progression: Double?
        let frame: CGRect
    }

    /// SYN-013: lectura más adelantada de otro dispositivo que se ofrece seguir.
    struct ContinueFrom: Equatable {
        let deviceName: String
        let percent: Int?
    }

    private(set) var phase: Phase = .loading
    /// Lo guardado (RDR-014). El lector aplica `settings`, que en Gratis es Clásico y serifa (PLN-015).
    private(set) var savedSettings: ReaderSettings
    /// PLN-015: la hoja de función paga a la vista, o nil.
    private(set) var paidFeature: PaidFeature?
    /// PLN-015: P01 sobre el lector, después de "Ver planes".
    var plansOpen = false
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
    /// ANN-008: las notas del libro, que el lector muestra subrayadas.
    private(set) var bookNotes: [Note] = []
    /// ANN-008, ANN-009: el editor de nota a la vista, o nil.
    private(set) var noteEditor: NoteEditor?
    /// ANN-008, K-332: la selección con su menú a la vista, o nil.
    private(set) var selection: TextSelection?
    /// Cambia cada vez que hay que quitar la selección del libro.
    private(set) var clearSelectionRequest = 0

    private let bookId: String
    private let source: ReaderBookSource
    private let positions: ReadingPositionRepository
    private let settingsRepository: ReaderSettingsRepository
    private let plan: @MainActor () -> PlanId
    private var paidAfterSettings = false
    private var plansAfterPaid = false
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
    private let notes: NoteRepository?
    /// ANN-010: se abrió desde una nota; el libro abre en su lugar y no en la última posición.
    private let noteId: String?
    private let clock: @Sendable () -> Date
    /// SYN-016: se guardó o borró una nota; la sincronización la sube enseguida.
    private let notesChanged: @MainActor () -> Void

    /// - Parameters:
    ///   - sync: nil si la app funciona solo en local.
    ///   - plan: el plan de la cuenta (PLN-015); sin dato, Gratis.
    ///   - notes: las notas del dispositivo (ANN-003); nil, sin notas.
    ///   - noteId: abrir en el lugar de esta nota (ANN-010).
    init(
        bookId: String,
        source: ReaderBookSource,
        positions: ReadingPositionRepository,
        settings: ReaderSettingsRepository,
        saveDelay: Duration = .milliseconds(250),
        sync: ReaderPositionSync? = nil,
        plan: @escaping @MainActor () -> PlanId = { .free },
        notes: NoteRepository? = nil,
        noteId: String? = nil,
        clock: @escaping @Sendable () -> Date = { .now },
        notesChanged: @escaping @MainActor () -> Void = {}
    ) {
        self.notesChanged = notesChanged
        self.notes = notes
        self.noteId = noteId
        self.clock = clock
        self.bookId = bookId
        self.source = source
        self.positions = positions
        settingsRepository = settings
        self.saveDelay = saveDelay
        self.sync = sync
        self.plan = plan
        savedSettings = settings.load()
    }

    func load() async {
        guard phase == .loading, choice == nil else { return }
        // ANN-010: abrir en una nota no pregunta por la posición de otro dispositivo: el usuario eligió el lugar.
        if let noteId, let note = await notes?.note(id: noteId), note.bookId == bookId {
            await openBook(startAt: note.locatorJson)
            return
        }
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

    /// - Parameter startAt: locator donde abrir en lugar de la última posición (ANN-010).
    private func openBook(startAt: String? = nil) async {
        do {
            var book = try await source.open(bookId: bookId)
            if let startAt {
                book = OpenedBook(
                    title: book.title, toc: book.toc, readingOrder: book.readingOrder, initialLocatorJson: startAt,
                    publication: book.publication
                )
            }
            chapters = book.toc.map { ChapterEntry(title: $0.title, href: $0.href) }
            readingOrder = book.readingOrder
            let topLevel = book.toc.filter { $0.depth == 0 }.map { BodyEntry(title: $0.title, href: $0.href) }
            bodyEnd = BodyEndDetector(readingOrder: readingOrder, backMatterStart: backMatterStart(toc: topLevel, readingOrder: readingOrder))
            await reloadNotes()
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

    // MARK: Notas (ANN-008, ANN-009)

    /// K-332: el navegador avisa la selección nueva, o nil cuando desapareció.
    func selectionChanged(_ selection: TextSelection?) {
        guard notes != nil || selection == nil else { return }
        self.selection = selection
    }

    /// ANN-008: "Nota" en el menú de la selección.
    func noteFromSelection() {
        guard let selection else { return }
        onTextSelected(locatorJson: selection.locatorJson, quote: selection.quote, progression: selection.progression)
        dismissSelection()
    }

    /// Cierra el menú y quita la selección del libro (después de Nota, Copiar o Compartir).
    func dismissSelection() {
        selection = nil
        clearSelectionRequest += 1
    }

    /// ANN-008: el usuario eligió "Nota" sobre un texto seleccionado. Una selección vacía no abre nada.
    func onTextSelected(locatorJson: String, quote: String, progression: Double?) {
        let text = quote.trimmingCharacters(in: .whitespacesAndNewlines)
        guard notes != nil, !text.isEmpty else { return }
        settingsOpen = false
        noteEditor = NoteEditor(noteId: nil, quote: text, text: "", locatorJson: locatorJson, progression: progression ?? 0)
    }

    /// ANN-009: el usuario tocó un subrayado: se muestra su nota para leerla, editarla o borrarla.
    func openNote(id: String) async {
        guard let note = await notes?.note(id: id) else { return }
        settingsOpen = false
        noteEditor = NoteEditor(
            noteId: note.id, quote: note.quote, text: note.text, locatorJson: note.locatorJson, progression: note.progression
        )
    }

    /// ANN-008, ANN-005: guarda la nota y cierra el editor. Una nota sin texto no se guarda y el editor sigue abierto.
    func saveNote(_ text: String) async {
        guard let editor = noteEditor, let notes else { return }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        noteEditor = nil
        let now = Int64((clock().timeIntervalSince1970 * 1000).rounded())
        if let id = editor.noteId, var existing = await notes.note(id: id) {
            existing.text = body
            existing.updatedAt = now
            await notes.save(existing)
        } else {
            await notes.save(
                Note(
                    id: UUID().uuidString.lowercased(), bookId: bookId, locatorJson: editor.locatorJson, quote: editor.quote,
                    text: body, progression: editor.progression, createdAt: now, updatedAt: now
                )
            )
        }
        notesChanged()
        await reloadNotes()
    }

    /// ANN-009: borra la nota abierta (la pantalla ya pidió confirmación); el subrayado se va con ella.
    func deleteNote() async {
        guard let id = noteEditor?.noteId, let notes else { return }
        noteEditor = nil
        await notes.delete(id: id)
        notesChanged()
        await reloadNotes()
    }

    /// ANN-008: cancelar no guarda nada.
    func closeNote() { noteEditor = nil }

    private func reloadNotes() async {
        bookNotes = await notes?.notes(bookId: bookId) ?? []
    }

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

    /// Lo que el lector aplica: en Gratis, Clásico y serifa (PLN-015).
    var settings: ReaderSettings { effectiveReaderSettings(savedSettings, plan: plan()) }

    /// PLN-015: la opción lleva la marca "Lector" y abre la hoja de función paga.
    func isLocked(_ theme: ReadingTheme) -> Bool { theme.isPaid && !plan().hasThemesAndFonts }
    func isLocked(_ font: ReaderFont) -> Bool { font.isPaid && !plan().hasThemesAndFonts }

    func setTheme(_ theme: ReadingTheme) {
        guard !isLocked(theme) else { return requestThemesAndFonts() }
        update(savedSettings.withTheme(theme))
    }

    func setFont(_ font: ReaderFont) {
        guard !isLocked(font) else { return requestThemesAndFonts() }
        update(savedSettings.withFont(font))
    }

    func setLineSpacing(_ spacing: LineSpacing) { update(savedSettings.withLineSpacing(spacing)) }
    func biggerFont() { update(savedSettings.biggerFont()) }
    func smallerFont() { update(savedSettings.smallerFont()) }

    private func update(_ newSettings: ReaderSettings) {
        guard newSettings != savedSettings else { return }
        savedSettings = newSettings
        settingsRepository.save(newSettings)
    }

    // MARK: Función paga (PLN-015)

    /// El panel se cierra y, cuando terminó de cerrarse, aparece la hoja: dos hojas a la vez no se pueden.
    private func requestThemesAndFonts() {
        if settingsOpen {
            paidAfterSettings = true
            settingsOpen = false
        } else {
            paidFeature = .themesAndFonts
        }
    }

    /// El panel terminó de cerrarse.
    func settingsDismissed() {
        guard paidAfterSettings else { return }
        paidAfterSettings = false
        paidFeature = .themesAndFonts
    }

    /// "Ahora no".
    func dismissPaidFeature() { paidFeature = nil }

    /// "Ver planes": cierra la hoja y abre P01 cuando terminó de cerrarse.
    func seePlans() {
        plansAfterPaid = true
        paidFeature = nil
    }

    func paidFeatureDismissed() {
        guard plansAfterPaid else { return }
        plansAfterPaid = false
        plansOpen = true
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
