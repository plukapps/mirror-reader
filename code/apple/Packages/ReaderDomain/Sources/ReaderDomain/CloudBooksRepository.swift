import Foundation

/// Portada local de un libro ya subido que aún no está en la nube (LIB-012).
public struct PendingCover: Equatable, Sendable {
    public let bookId: String
    public let file: URL

    public init(bookId: String, file: URL) {
        self.bookId = bookId
        self.file = file
    }
}

/// Lo que la sincronización necesita de la base local. La implementación vive en la capa de datos de la app.
public protocol CloudBooksRepository: Sendable {
    /// Agrega como "solo en la nube" los libros que faltan (LIB-007). Los que ya están no cambian.
    func addCloudOnly(_ books: [RemoteBook]) async throws
    /// Libros que están solo en la nube, para bajarlos (LIB-007).
    func cloudOnlyBookIds() async throws -> [String]
    /// Portadas de libros ya subidos que no están anotadas como subidas (LIB-012).
    func coversToUpload() async throws -> [PendingCover]
    /// Anota que la portada ya está en la nube.
    func markCoverUploaded(bookId: String) async throws
    /// Libros sin portada local, para intentar bajarla (LIB-012).
    func booksWithoutCover() async throws -> [String]
    /// Archivo temporal donde bajar algo antes de instalarlo.
    func newTempFile() -> URL
    /// Mueve la portada bajada a su lugar y la anota en el libro (también como subida: vino de la nube).
    func installCover(bookId: String, from file: URL) async throws
}
