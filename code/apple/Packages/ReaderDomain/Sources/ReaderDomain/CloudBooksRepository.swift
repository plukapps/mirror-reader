import Foundation

/// Lo que la sincronización necesita de la base local. La implementación vive en la capa de datos de la app.
public protocol CloudBooksRepository: Sendable {
    /// Agrega como "solo en la nube" los libros que faltan (LIB-007). Los que ya están no cambian.
    func addCloudOnly(_ books: [RemoteBook]) async throws
    /// Guarda las posiciones que son más nuevas que la local (`shouldUseRemotePosition`). Devuelve cuántas guardó.
    func applyRemotePositions(_ positions: [RemotePosition]) async throws -> Int
    /// Libros sin portada local, para intentar bajarla (LIB-012).
    func booksWithoutCover() async throws -> [String]
    /// Archivo temporal donde bajar algo antes de instalarlo.
    func newTempFile() -> URL
    /// Mueve la portada bajada a su lugar y la anota en el libro.
    func installCover(bookId: String, from file: URL) async throws
}
