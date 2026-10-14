import Foundation
import ReaderDomain
import SwiftData

/// Base local de la biblioteca (ADR 0002, ADR 0012): la fuente de verdad de lo que muestra la app.
/// Es un `ModelActor`: todo acceso a SwiftData pasa por su propio contexto, fuera del hilo principal.
actor LibraryStore: ModelActor, LibraryRepository, CloudBooksRepository, BookUploadRepository, BookFiles, PositionSyncStore,
    ReadingPositionRepository, BookEditor, BookRemovals, KnownBooks, NoteRepository, NoteSyncStore {
    nonisolated let modelContainer: ModelContainer
    nonisolated let modelExecutor: any ModelExecutor
    private let files: LibraryFiles
    private let now: @Sendable () -> Date

    init(container: ModelContainer, files: LibraryFiles, now: @escaping @Sendable () -> Date = { .now }) {
        modelContainer = container
        modelExecutor = DefaultSerialModelExecutor(modelContext: ModelContext(container))
        self.files = files
        self.now = now
    }

    /// Base guardada en disco, en `Application Support/Library.store`.
    static func persistentContainer() throws -> ModelContainer {
        let configuration = ModelConfiguration("Library", schema: schema)
        return try ModelContainer(for: schema, configurations: configuration)
    }

    /// Base en memoria, para los tests.
    static func inMemoryContainer() throws -> ModelContainer {
        try ModelContainer(for: schema, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
    }

    private static let schema = Schema([BookRecord.self, PositionRecord.self, RemovedBookRecord.self, NoteRecord.self])

    // MARK: LibraryRepository

    func books() async -> [LibraryBook] {
        do {
            let records = try modelContext.fetch(FetchDescriptor<BookRecord>(sortBy: [SortDescriptor(\.addedAt, order: .reverse)]))
            let positions = try positionsByBook()
            return records.map { record in
                let position = positions[record.id]
                return LibraryBook(
                    id: record.id,
                    title: record.title,
                    author: record.author,
                    coverPath: record.hasCover ? files.coverFile(bookId: record.id).path : nil,
                    progressPercent: progressPercent(position.flatMap { positionProgression(locatorJson: $0.locatorJson, progress: $0.progress) }),
                    lastReadAt: position.map { Date(timeIntervalSince1970: Double($0.readAt) / 1000) },
                    addedAt: record.addedAt,
                    isDownloaded: record.isDownloaded,
                    isUploaded: record.uploadedAt != nil,
                    coverVersion: record.coverVersion
                )
            }
        } catch {
            return []
        }
    }

    // MARK: CloudBooksRepository

    func addCloudOnly(_ books: [RemoteBook]) async throws {
        // LIB-020: un libro quitado aquí que la nube todavía no tiene borrado no vuelve. Si el borrado ya se completó y
        // la nube lo lista vivo, alguien lo volvió a importar: se olvida la marca y vuelve.
        let removed = try modelContext.fetch(FetchDescriptor<RemovedBookRecord>())
        let alive = Set(books.map(\.id))
        for record in removed where record.isCloudDeleted && alive.contains(record.id) { modelContext.delete(record) }
        let records = Dictionary(try modelContext.fetch(FetchDescriptor<BookRecord>()).map { ($0.id, $0) }) { first, _ in first }
        // LIB-021: el libro entró a la biblioteca cuando entró a la nube; esa fecha manda también sobre la de un libro
        // importado aquí o visto antes. Sin fecha de la nube se conserva la que hay.
        for book in books {
            if let record = records[book.id], let createdAt = book.createdAt, record.addedAt != createdAt {
                record.addedAt = createdAt
            }
        }
        let existing = Set(records.keys).union(removed.filter { !$0.isCloudDeleted }.map(\.id))
        let seenAt = now()
        for book in books where !existing.contains(book.id) {
            modelContext.insert(
                BookRecord(
                    id: book.id, title: book.title, author: book.joinedAuthors, hasCover: false,
                    addedAt: book.createdAt ?? seenAt, sizeBytes: book.sizeBytes, isDownloaded: false, uploadedAt: seenAt
                )
            )
        }
        try modelContext.save()
    }

    // MARK: PositionSyncStore (SYN-011, SYN-012)

    func local(bookId: String) async -> LocalPosition? {
        guard let record = try? positionRecord(bookId) else { return nil }
        return LocalPosition(position: readingPosition(record), isSynced: record.isSynced)
    }

    /// Solo las de libros que ya están en la nube: el servidor exige que el libro exista.
    func pendingPositions() async -> [ReadingPosition] {
        do {
            let uploaded = Set(
                try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.uploadedAt != nil })).map(\.id)
            )
            return try modelContext.fetch(FetchDescriptor<PositionRecord>(predicate: #Predicate { !$0.isSynced }))
                .filter { uploaded.contains($0.bookId) }
                .map(readingPosition)
        } catch {
            return []
        }
    }

    func markSynced(bookId: String, readAt: Int64) async {
        // Si mientras se enviaba se leyó más, la lectura nueva sigue pendiente.
        guard let record = try? positionRecord(bookId), record.readAt == readAt else { return }
        record.isSynced = true
        try? modelContext.save()
    }

    func markPending(bookId: String) async {
        guard let record = try? positionRecord(bookId) else { return }
        record.isSynced = false
        try? modelContext.save()
    }

    func applyRemote(_ position: ReadingPosition) async {
        do {
            if let record = try positionRecord(position.bookId) {
                // Gana la lectura más reciente (SYN-003): una más vieja no pisa la local.
                guard record.readAt < position.readAt else { return }
                record.locatorJson = position.locatorJson
                record.readAt = position.readAt
                record.progress = position.progress
                record.isSynced = true
            } else {
                modelContext.insert(
                    PositionRecord(
                        bookId: position.bookId, locatorJson: position.locatorJson, readAt: position.readAt,
                        progress: position.progress, isSynced: true
                    )
                )
            }
            try modelContext.save()
        } catch {
            // Llegará de nuevo con la próxima escucha o al abrir el libro.
        }
    }

    private func positionRecord(_ bookId: String) throws -> PositionRecord? {
        try modelContext.fetch(FetchDescriptor<PositionRecord>(predicate: #Predicate { $0.bookId == bookId })).first
    }

    private func readingPosition(_ record: PositionRecord) -> ReadingPosition {
        ReadingPosition(bookId: record.bookId, locatorJson: record.locatorJson, progress: record.progress, readAt: record.readAt)
    }

    // MARK: ReadingPositionRepository (RDR-006)

    func position(bookId: String) async -> SavedPosition? {
        guard let record = try? positionsByBook()[bookId] else { return nil }
        return SavedPosition(locatorJson: record.locatorJson, progress: record.progress)
    }

    func save(bookId: String, position: SavedPosition) async {
        let readAt = Int64((now().timeIntervalSince1970 * 1000).rounded())
        do {
            if let record = try positionsByBook()[bookId] {
                record.locatorJson = position.locatorJson
                record.progress = position.progress
                record.readAt = readAt
                record.isSynced = false
            } else {
                modelContext.insert(
                    PositionRecord(
                        bookId: bookId, locatorJson: position.locatorJson, readAt: readAt,
                        progress: position.progress, isSynced: false
                    )
                )
            }
            try modelContext.save()
        } catch {
            // Una posición que no se guarda no corta la lectura; la siguiente lo vuelve a intentar.
        }
    }

    // MARK: BookFiles (LIB-007)

    func isDownloaded(bookId: String) async -> Bool {
        guard (try? record(bookId))?.isDownloaded == true else { return false }
        return FileManager.default.fileExists(atPath: files.bookFile(bookId: bookId).path)
    }

    /// Instala un EPUB bajado de la nube. Antes comprueba que su hash sea el del libro: un archivo que no coincide
    /// no se instala (como Android, que reimporta lo bajado).
    func installBook(bookId: String, from file: URL) async throws {
        guard try LibraryFiles.sha256(of: file) == bookId else {
            throw BookOpenError("El archivo descargado no coincide con el libro.")
        }
        try files.installBook(bookId: bookId, from: file)
        try record(bookId)?.isDownloaded = true
        try modelContext.save()
    }

    func booksWithoutCover() async throws -> [String] {
        try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { !$0.hasCover })).map(\.id)
    }

    nonisolated func newTempFile() -> URL { files.newTempFile() }

    func installCover(bookId: String, from file: URL) async throws {
        try files.installCover(bookId: bookId, from: file)
        let records = try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.id == bookId }))
        // Vino de la nube: ya está allí y no hay que subirla.
        records.forEach {
            $0.hasCover = true
            $0.isCoverUploaded = true
        }
        try modelContext.save()
    }

    func cloudOnlyBookIds() async throws -> [String] {
        try modelContext.fetch(
            FetchDescriptor<BookRecord>(
                predicate: #Predicate { !$0.isDownloaded }, sortBy: [SortDescriptor(\.addedAt, order: .reverse)]
            )
        ).map(\.id)
    }

    func coversToUpload() async throws -> [PendingCover] {
        try modelContext.fetch(
            FetchDescriptor<BookRecord>(predicate: #Predicate { $0.uploadedAt != nil && $0.hasCover && !$0.isCoverUploaded })
        ).map { PendingCover(bookId: $0.id, file: files.coverFile(bookId: $0.id)) }
    }

    func markCoverUploaded(bookId: String) async throws {
        try record(bookId)?.isCoverUploaded = true
        try modelContext.save()
    }

    // MARK: BookUploadRepository (SYN-001)

    func pending() async throws -> [PendingUpload] {
        let records = try modelContext.fetch(
            FetchDescriptor<BookRecord>(
                predicate: #Predicate { $0.isDownloaded && $0.uploadedAt == nil }, sortBy: [SortDescriptor(\.addedAt)]
            )
        )
        return records.compactMap { record in
            let file = files.bookFile(bookId: record.id)
            // El tamaño sale del archivo: es lo que cuenta la cuota (LIB-009).
            guard let size = (try? FileManager.default.attributesOfItem(atPath: file.path))?[.size] as? NSNumber else {
                return nil
            }
            let cover = record.hasCover ? files.coverFile(bookId: record.id) : nil
            return PendingUpload(
                book: RemoteBook(
                    id: record.id, title: record.title, authors: record.author.map { [$0] } ?? [], sizeBytes: size.int64Value
                ),
                file: file,
                cover: cover.flatMap { FileManager.default.fileExists(atPath: $0.path) ? $0 : nil }
            )
        }
    }

    func markUploaded(bookId: String, sizeBytes: Int64, coverUploaded: Bool) async throws {
        guard let book = try record(bookId) else { return }
        book.uploadedAt = now()
        book.sizeBytes = sizeBytes
        book.isCoverUploaded = book.isCoverUploaded || coverUploaded
        try modelContext.save()
    }

    // MARK: Libros importados (LIB-001 a LIB-004)

    /// Lo que la importación necesita saber de un libro que ya está en la base, o nil si no está.
    func importedBook(id: String) -> (title: String, isDownloaded: Bool)? {
        guard let book = try? record(id) else { return nil }
        return (book.title, book.isDownloaded)
    }

    /// Registra un libro recién importado, con su archivo ya en `books/{id}.epub`.
    func addImported(id: String, title: String, author: String?, hasCover: Bool, sizeBytes: Int64) throws {
        // Si el usuario lo había quitado (LIB-020), importarlo de nuevo lo devuelve a la biblioteca.
        try modelContext.delete(model: RemovedBookRecord.self, where: #Predicate { $0.id == id })
        modelContext.insert(
            BookRecord(
                id: id, title: title, author: author, hasCover: hasCover, addedAt: now(),
                sizeBytes: sizeBytes, isDownloaded: true, uploadedAt: nil
            )
        )
        try modelContext.save()
    }

    /// Un libro solo en la nube que el usuario importó a mano: conserva los metadatos de la nube y suma el archivo
    /// y la portada, como `markDownloaded` de Android.
    func markImported(id: String, hasCover: Bool) throws {
        guard let book = try record(id) else { return }
        book.isDownloaded = true
        book.hasCover = book.hasCover || hasCover
        try modelContext.save()
    }

    // MARK: BookEditor (LIB-019, LIB-020)

    func replaceCover(bookId: String, imageData: Data) async -> Bool {
        guard let jpeg = CoverImages.jpeg(from: imageData) else { return false }
        do {
            // Se escribe aparte y se reemplaza de una vez: una pantalla nunca lee una portada a medio escribir.
            let temp = files.newTempFile()
            try jpeg.write(to: temp)
            try files.installCover(bookId: bookId, from: temp)
            guard let book = try record(bookId) else { return false }
            book.hasCover = true
            book.coverVersion = Int64((now().timeIntervalSince1970 * 1000).rounded())
            try modelContext.save()
            return true
        } catch {
            return false
        }
    }

    func remove(bookId: String) async {
        // Un libro que nunca se subió no tiene nada en la nube: queda hecho al quitarlo.
        let inCloud = (try? record(bookId))?.uploadedAt != nil
        removeLocally(bookId: bookId, isCloudDeleted: !inCloud)
    }

    // MARK: KnownBooks (SYN-015)

    func knownBookIds() async -> Set<String> {
        Set((try? modelContext.fetch(FetchDescriptor<BookRecord>()))?.map(\.id) ?? [])
    }

    // MARK: BookRemovals (LIB-020, LIB-008)

    func pendingCloudRemovals() async -> [String] {
        let pending = try? modelContext.fetch(
            FetchDescriptor<RemovedBookRecord>(predicate: #Predicate { !$0.isCloudDeleted }, sortBy: [SortDescriptor(\.removedAt)])
        )
        return pending?.map(\.id) ?? []
    }

    func markCloudRemoved(bookId: String) async {
        let records = try? modelContext.fetch(FetchDescriptor<RemovedBookRecord>(predicate: #Predicate { $0.id == bookId }))
        records?.forEach { $0.isCloudDeleted = true }
        try? modelContext.save()
    }

    func removeDeletedInCloud(bookIds: [String]) async {
        for bookId in bookIds {
            guard let book = try? record(bookId), book.uploadedAt != nil else { continue }
            removeLocally(bookId: bookId, isCloudDeleted: true)
        }
    }

    private func removeLocally(bookId: String, isCloudDeleted: Bool) {
        do {
            try modelContext.delete(model: RemovedBookRecord.self, where: #Predicate { $0.id == bookId })
            modelContext.insert(RemovedBookRecord(id: bookId, removedAt: now(), isCloudDeleted: isCloudDeleted))
            try modelContext.delete(model: BookRecord.self, where: #Predicate { $0.id == bookId })
            try modelContext.delete(model: PositionRecord.self, where: #Predicate { $0.bookId == bookId })
            // ANN-011, SYN-007: las notas que la nube nunca tuvo se eliminan; las demás se marcan borradas, así el borrado
            // llega a los otros dispositivos.
            try modelContext.delete(model: NoteRecord.self, where: #Predicate { $0.bookId == bookId && $0.syncedAt == nil })
            let at = millis(now())
            for record in try modelContext.fetch(FetchDescriptor<NoteRecord>(predicate: #Predicate { $0.bookId == bookId && $0.deletedAt == nil })) {
                record.deletedAt = at
                record.updatedAt = at
            }
            try modelContext.save()
        } catch {
            modelContext.rollback()
            return
        }
        try? FileManager.default.removeItem(at: files.bookFile(bookId: bookId))
        try? FileManager.default.removeItem(at: files.coverFile(bookId: bookId))
    }

    /// El libro ya tiene portada en este dispositivo (LIB-012).
    func hasCover(bookId: String) -> Bool {
        (try? record(bookId))?.hasCover == true
    }

    /// Título del libro, para el lector.
    func title(bookId: String) -> String? {
        (try? record(bookId))?.title
    }

    private func record(_ id: String) throws -> BookRecord? {
        try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.id == id })).first
    }

    private func positionsByBook() throws -> [String: PositionRecord] {
        let positions = try modelContext.fetch(FetchDescriptor<PositionRecord>())
        return Dictionary(positions.map { ($0.bookId, $0) }, uniquingKeysWith: { first, _ in first })
    }

    // MARK: Cerrar sesión (PRF-009)

    /// Borra todos los libros, posiciones, notas y quitados de la base. Los archivos los borra `LibraryFiles.deleteAll()`.
    func deleteAll() throws {
        try modelContext.delete(model: BookRecord.self)
        try modelContext.delete(model: NoteRecord.self)
        try modelContext.delete(model: PositionRecord.self)
        try modelContext.delete(model: RemovedBookRecord.self)
        try modelContext.save()
    }


    // MARK: NoteRepository (ANN-003 a ANN-006, ANN-010)

    func notes(bookId: String) async -> [Note] {
        let records = try? modelContext.fetch(
            FetchDescriptor<NoteRecord>(
                predicate: #Predicate { $0.bookId == bookId && $0.deletedAt == nil },
                sortBy: [SortDescriptor(\.progression), SortDescriptor(\.createdAt)]
            )
        )
        return records?.map(note) ?? []
    }

    func note(id: String) async -> Note? {
        (try? noteRecord(id)).flatMap { $0 }.flatMap { $0.deletedAt == nil ? note($0) : nil }
    }

    /// Una nota nueva se crea; una existente cambia solo su texto (ANN-005). Las dos quedan pendientes de subir.
    func save(_ note: Note) async {
        do {
            if let record = try noteRecord(note.id) {
                guard record.deletedAt == nil else { return }
                record.text = note.text
                record.updatedAt = note.updatedAt
            } else {
                modelContext.insert(
                    NoteRecord(
                        id: note.id, bookId: note.bookId, locatorJson: note.locatorJson, quote: note.quote, text: note.text,
                        progression: note.progression, createdAt: note.createdAt, updatedAt: note.updatedAt
                    )
                )
            }
            try modelContext.save()
        } catch {
            modelContext.rollback()
        }
    }

    /// SYN-007: una nota que la nube nunca tuvo se elimina; si no, se marca y el borrado se sube.
    func delete(id: String) async {
        guard let record = try? noteRecord(id), record.deletedAt == nil else { return }
        if record.syncedAt == nil {
            modelContext.delete(record)
        } else {
            let at = millis(now())
            record.deletedAt = at
            record.updatedAt = at
        }
        try? modelContext.save()
    }

    // MARK: NoteSyncStore (SYN-016, SYN-005, SYN-007)

    func pendingNotes() async -> [SyncNote] {
        let uploaded = Set((try? modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.uploadedAt != nil })))?.map(\.id) ?? [])
        let records = (try? modelContext.fetch(FetchDescriptor<NoteRecord>())) ?? []
        // Una nota viva solo si su libro ya está en la nube (el servidor exige que exista); una borrada siempre: ya estuvo
        // en la nube, así que su libro también.
        return records
            .filter { ($0.syncedAt ?? -1) < $0.updatedAt && ($0.deletedAt != nil || uploaded.contains($0.bookId)) }
            .map { SyncNote(note: note($0), deletedAt: $0.deletedAt) }
    }

    func hasUnsyncedNotes(syncOn: Bool) async -> Bool {
        let records = (try? modelContext.fetch(FetchDescriptor<NoteRecord>())) ?? []
        // Con la sincronización apagada, cualquier nota viva es solo de este dispositivo.
        return syncOn ? records.contains { ($0.syncedAt ?? -1) < $0.updatedAt } : records.contains { $0.deletedAt == nil }
    }

    func markNoteSynced(id: String, editedAt: Int64) async {
        guard let record = try? noteRecord(id), record.updatedAt == editedAt else { return }
        record.syncedAt = editedAt
        try? modelContext.save()
    }

    func applyRemote(_ remote: SyncNote) async {
        let local = try? noteRecord(remote.note.id)
        if remote.deletedAt != nil {
            // Un borrado más reciente que la versión local la elimina; uno más viejo que una edición local, no.
            if let local, local.updatedAt <= remote.editedAt { modelContext.delete(local) }
        } else if let local {
            guard local.updatedAt < remote.editedAt else { return }
            local.text = remote.note.text
            local.updatedAt = remote.editedAt
            local.syncedAt = remote.editedAt
            local.deletedAt = nil
        } else {
            let note = remote.note
            let record = NoteRecord(
                id: note.id, bookId: note.bookId, locatorJson: note.locatorJson, quote: note.quote, text: note.text,
                progression: note.progression, createdAt: note.createdAt, updatedAt: remote.editedAt
            )
            record.syncedAt = remote.editedAt
            modelContext.insert(record)
        }
        try? modelContext.save()
    }

    func purgeSyncedNoteDeletions() async {
        let records = (try? modelContext.fetch(FetchDescriptor<NoteRecord>(predicate: #Predicate { $0.deletedAt != nil }))) ?? []
        for record in records where (record.syncedAt ?? -1) >= record.updatedAt { modelContext.delete(record) }
        try? modelContext.save()
    }

    private func millis(_ date: Date) -> Int64 {
        Int64((date.timeIntervalSince1970 * 1000).rounded())
    }

    private func noteRecord(_ id: String) throws -> NoteRecord? {
        try modelContext.fetch(FetchDescriptor<NoteRecord>(predicate: #Predicate { $0.id == id })).first
    }

    private func note(_ record: NoteRecord) -> Note {
        Note(
            id: record.id, bookId: record.bookId, locatorJson: record.locatorJson, quote: record.quote, text: record.text,
            progression: record.progression, createdAt: record.createdAt, updatedAt: record.updatedAt
        )
    }

}
