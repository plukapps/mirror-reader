import Foundation
import Observation
import ReaderDomain

/// Estado de Inicio (HOM-001 a HOM-003, HOM-008 a HOM-010), con la importación (LIB-001) y la apertura del lector.
@MainActor
@Observable
final class HomeViewModel {
    private(set) var loading = true
    private(set) var content = HomeContent.empty
    /// Se calcula al abrir la pantalla y no cambia mientras está abierta.
    let greeting: Greeting

    /// Libro abierto en el lector, o nil (RDR-007).
    private(set) var readerBookId: String?
    /// Avisos pendientes de mostrar, el primero arriba (resultado de importar, LIB-001).
    private(set) var messages: [String] = []

    private let library: LibraryRepository
    private let sync: Sync?
    private let importer: BookImporter?
    private var synced = false

    /// Trae la nube a la base local (`LibrarySync`) y llama al argumento cada vez que la base cambió.
    typealias Sync = @Sendable (_ onChange: @escaping @Sendable () async -> Void) async -> LibrarySyncOutcome

    /// - Parameters:
    ///   - importer: nil donde no hay motor de EPUB (la Mac, ADR 0013): no se importa ni se abre el lector.
    ///   - sync: nil si la app funciona solo en local.
    init(
        library: LibraryRepository,
        now: Date = .now,
        calendar: Calendar = .current,
        importer: BookImporter? = nil,
        sync: Sync? = nil
    ) {
        self.library = library
        self.importer = importer
        self.sync = sync
        greeting = ReaderDomain.greeting(forHour: calendar.component(.hour, from: now))
    }

    /// Muestra primero lo local (ADR 0002) y, la primera vez, sincroniza y vuelve a leer la base cada vez que
    /// cambia (SYN-001).
    func load() async {
        await reload()
        guard let sync, !synced else { return }
        synced = true
        _ = await sync { [weak self] in await self?.reload() }
    }

    /// Vuelve a leer la base, por ejemplo al cerrar el lector (cambió el progreso).
    func refresh() async { await reload() }

    // MARK: Importar y abrir (LIB-001, RDR-007)

    var canImport: Bool { importer != nil }

    /// Importa en orden; un fallo no detiene a los demás (LIB-001). Avisa cuántos entraron y qué pasó con el resto.
    func importBooks(_ urls: [URL]) async {
        guard let importer, !urls.isEmpty else { return }
        var outcomes: [ImportOutcome] = []
        for url in urls { outcomes.append(await importer.importBook(from: url)) }
        messages += Self.messages(for: outcomes)
        await reload()
    }

    /// "Abrir con" desde otra app: importa el archivo y abre el libro, aunque ya estuviera en la biblioteca.
    func openIncoming(_ url: URL) async {
        guard let importer else { return }
        switch await importer.importBook(from: url) {
        case let .imported(bookId, _), let .alreadyInLibrary(bookId, _):
            await reload()
            readerBookId = bookId
        case let .rejected(message):
            messages.append(message)
        }
    }

    func open(bookId: String) {
        guard importer != nil else {
            messages.append(String(localized: "El lector llega más adelante a la Mac."))
            return
        }
        readerBookId = bookId
    }

    func closeReader() { readerBookId = nil }

    func dismissMessage() {
        if !messages.isEmpty { messages.removeFirst() }
    }

    private static func messages(for outcomes: [ImportOutcome]) -> [String] {
        let imported = outcomes.filter { if case .imported = $0 { true } else { false } }.count
        let summary: [String] = switch imported {
        case 0: []
        case 1: ["Se importó 1 libro."]
        default: ["Se importaron \(imported) libros."]
        }
        return summary + outcomes.compactMap {
            switch $0 {
            case .imported: nil
            case let .alreadyInLibrary(_, title): "«\(title)» ya está en tu biblioteca."
            case let .rejected(message): message
            }
        }
    }

    private func reload() async {
        content = homeContent(await library.books())
        loading = false
    }
}
