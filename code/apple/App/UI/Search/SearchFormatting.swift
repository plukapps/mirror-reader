import ReaderDomain

/// "Leyendo · 42 %", "Nuevo" o "Terminado", y "En la nube" si no está en el dispositivo (LIB-014, LIB-007).
func searchStatus(_ book: LibraryBook) -> String {
    let reading = switch book.status {
    case .reading: String(localized: "Leyendo · \(book.progressPercent ?? 0) %")
    case .new: String(localized: "Nuevo")
    case .finished: String(localized: "Terminado")
    }
    return book.isDownloaded ? reading : [reading, String(localized: "En la nube")].joined(separator: " · ")
}

/// "1 resultado", "3 resultados" (LIB-014). La pantalla lo muestra en mayúsculas.
func searchResultsCount(_ count: Int) -> String {
    count == 1 ? String(localized: "1 resultado") : String(localized: "\(count) resultados")
}
