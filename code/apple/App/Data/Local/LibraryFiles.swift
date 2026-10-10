import Foundation

/// Carpetas de la biblioteca en el dispositivo, con las mismas rutas que Android (ADR 0012).
struct LibraryFiles: Sendable {
    let root: URL

    /// `Application Support` de la app (en la Mac, dentro del contenedor del sandbox).
    static let standard = LibraryFiles(root: .applicationSupportDirectory)

    var booksDirectory: URL { root.appending(path: "books", directoryHint: .isDirectory) }
    var coversDirectory: URL { root.appending(path: "covers", directoryHint: .isDirectory) }
    var tempDirectory: URL { root.appending(path: "tmp", directoryHint: .isDirectory) }

    func bookFile(bookId: String) -> URL { booksDirectory.appending(path: "\(bookId).epub") }
    func coverFile(bookId: String) -> URL { coversDirectory.appending(path: "\(bookId).jpg") }

    func newTempFile() -> URL {
        try? FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
        return tempDirectory.appending(path: UUID().uuidString)
    }

    /// Mueve `file` a la portada del libro, reemplazando la anterior si la había.
    func installCover(bookId: String, from file: URL) throws {
        try install(file, at: coverFile(bookId: bookId))
    }

    /// Mueve `file` al archivo EPUB del libro, reemplazando el anterior si lo había.
    func installBook(bookId: String, from file: URL) throws {
        try install(file, at: bookFile(bookId: bookId))
    }

    private func install(_ file: URL, at destination: URL) throws {
        let fileManager = FileManager.default
        try fileManager.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        if fileManager.fileExists(atPath: destination.path) {
            _ = try fileManager.replaceItemAt(destination, withItemAt: file)
        } else {
            try fileManager.moveItem(at: file, to: destination)
        }
    }
}
