import Foundation

/// Localizador de un libro marcado como no leído (LIB-018): viaja como una posición más, así llega a los otros
/// dispositivos, y el lector lo abre desde el principio porque no apunta a ningún capítulo (no tiene `href`). No es
/// `"{}"`: el lector guarda eso cuando no puede serializar una posición real. Igual que Android.
public let unreadLocator = #"{"unread":true}"#

/// Progresión de un libro que tiene posición guardada. Una posición "no leído" cuenta como sin abrir (nil), salvo
/// que se haya marcado terminado sin abrirlo.
public func positionProgression(locatorJson: String, progress: Double?) -> Double? {
    if locatorJson == unreadLocator && (progress ?? 0) < 1 { return nil }
    return progress
}

/// Cambios del usuario sobre un libro de este dispositivo (LIB-019, LIB-020).
public protocol BookEditor: Sendable {
    /// Usa la imagen (`imageData`, en cualquier formato de foto) como portada. Falso si no se pudo leer o guardar.
    func replaceCover(bookId: String, imageData: Data) async -> Bool
    /// Borra el libro de este dispositivo (registro, posición, archivo y portada) y recuerda que se quitó, para que
    /// la sincronización no lo vuelva a traer.
    func remove(bookId: String) async
}

/// Acciones del menú de un libro (LIB-016 a LIB-020). "Leer libro" es navegación y no pasa por aquí.
public struct BookActions: Sendable {
    private let positions: ReadingPositionRepository
    private let editor: BookEditor
    private let requestFlush: @Sendable () async -> Void

    /// - Parameter requestFlush: envía las posiciones pendientes (SYN-011). Sin sesión, no hace nada.
    public init(positions: ReadingPositionRepository, editor: BookEditor, requestFlush: @escaping @Sendable () async -> Void) {
        self.positions = positions
        self.editor = editor
        self.requestFlush = requestFlush
    }

    /// Deja el libro al 100 % conservando su última posición (LIB-018).
    public func markFinished(bookId: String) async {
        let locator = await positions.position(bookId: bookId)?.locatorJson ?? unreadLocator
        await positions.save(bookId: bookId, position: SavedPosition(locatorJson: locator, progress: 1))
        await requestFlush()
    }

    /// El libro vuelve a estar sin empezar y se abre desde el principio (LIB-018).
    public func markUnread(bookId: String) async {
        await positions.save(bookId: bookId, position: SavedPosition(locatorJson: unreadLocator, progress: 0))
        await requestFlush()
    }

    public func setCover(bookId: String, imageData: Data) async -> Bool {
        await editor.replaceCover(bookId: bookId, imageData: imageData)
    }

    public func remove(bookId: String) async {
        await editor.remove(bookId: bookId)
    }
}
