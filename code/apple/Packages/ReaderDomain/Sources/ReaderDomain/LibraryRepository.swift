/// Fuente de los libros de la biblioteca. La implementación vive en la capa de datos de la app.
public protocol LibraryRepository: Sendable {
    /// Libros con el importado más reciente primero (lo que espera `homeContent`).
    func books() async -> [LibraryBook]
}
