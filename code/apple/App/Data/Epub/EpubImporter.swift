#if os(iOS)
import CryptoKit
import Foundation
import ReaderDomain
@preconcurrency import ReadiumShared
import UIKit

/// Importa un EPUB, como `EpubImporter` de Android: lo copia al almacenamiento de la app calculando su hash
/// (LIB-003), lo valida con Readium (LIB-002), lee título, autor y portada (LIB-004) y lo registra en la base
/// (ADR 0006). Las importaciones van de a una: dos del mismo archivo a la vez no lo duplican.
@MainActor
final class EpubImporter: BookImporter {
    private let store: LibraryStore
    private let files: LibraryFiles
    private let loader: PublicationLoader

    init(store: LibraryStore, files: LibraryFiles, loader: PublicationLoader) {
        self.store = store
        self.files = files
        self.loader = loader
    }

    private var running: Task<ImportOutcome, Never>?

    nonisolated func importBook(from url: URL) async -> ImportOutcome {
        await enqueue(url)
    }

    private func enqueue(_ url: URL) async -> ImportOutcome {
        let previous = running
        let task = Task { @MainActor in
            _ = await previous?.value
            return await importNow(url)
        }
        running = task
        return await task.value
    }

    private func importNow(_ source: URL) async -> ImportOutcome {
        let temp = files.newTempFile()
        let hash: String
        do {
            hash = try await Self.copyWithSha256(from: source, to: temp)
        } catch {
            try? FileManager.default.removeItem(at: temp)
            return .rejected(message: "No se pudo leer el archivo.")
        }

        // Un libro solo en la nube (LIB-007) no cuenta como ya importado: falta su archivo.
        let existing = await store.importedBook(id: hash)
        if let existing, existing.isDownloaded {
            try? FileManager.default.removeItem(at: temp)
            return .alreadyInLibrary(bookId: hash, title: existing.title)
        }

        let bookFile = files.bookFile(bookId: hash)
        do {
            try files.installBook(bookId: hash, from: temp)
        } catch {
            try? FileManager.default.removeItem(at: temp)
            return .rejected(message: "No se pudo guardar el libro.")
        }

        let publication: Publication
        do {
            publication = try await loader.open(bookFile)
        } catch {
            try? FileManager.default.removeItem(at: bookFile)
            return .rejected(message: (error as? BookOpenError)?.message ?? "No se pudo abrir el libro.")
        }
        defer { publication.close() }

        let title = publication.metadata.title?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty
            ?? Self.displayName(source)
        let author = publication.metadata.authors
            .map { $0.name.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
            .nonEmpty
        let hasCover = await saveCover(publication, to: files.coverFile(bookId: hash))
        do {
            if let existing {
                // Conserva los metadatos de la nube y suma el archivo y la portada.
                try await store.markImported(id: hash, hasCover: hasCover)
                return .imported(bookId: hash, title: existing.title)
            }
            let size = (try? FileManager.default.attributesOfItem(atPath: bookFile.path)[.size] as? Int64) ?? 0
            try await store.addImported(id: hash, title: title, author: author, hasCover: hasCover, sizeBytes: size)
            return .imported(bookId: hash, title: title)
        } catch {
            return .rejected(message: "No se pudo guardar el libro.")
        }
    }

    /// Portada en JPEG de hasta 600 px de alto, como Android (LIB-004).
    private func saveCover(_ publication: Publication, to target: URL) async -> Bool {
        guard case let .success(image?) = await publication.cover() else { return false }
        let scale = min(1, Self.maxCoverHeight / image.size.height)
        let size = CGSize(width: max(1, (image.size.width * scale).rounded()), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let scaled = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        guard let data = scaled.jpegData(compressionQuality: 0.85) else { return false }
        do {
            try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: target, options: .atomic)
            return true
        } catch {
            return false
        }
    }

    private static let maxCoverHeight: CGFloat = 600

    /// Copia `source` a `destination` y devuelve el SHA-256 del contenido en hexadecimal minúscula (LIB-003, ADR 0006).
    /// Pide acceso al archivo si viene de fuera de la app (selector de Archivos, "Abrir con").
    nonisolated static func copyWithSha256(from source: URL, to destination: URL) async throws -> String {
        try await Task.detached(priority: .userInitiated) {
            let scoped = source.startAccessingSecurityScopedResource()
            defer { if scoped { source.stopAccessingSecurityScopedResource() } }
            let input = try FileHandle(forReadingFrom: source)
            defer { try? input.close() }
            FileManager.default.createFile(atPath: destination.path, contents: nil)
            let output = try FileHandle(forWritingTo: destination)
            defer { try? output.close() }
            var hasher = SHA256()
            while let chunk = try input.read(upToCount: 1 << 16), !chunk.isEmpty {
                hasher.update(data: chunk)
                try output.write(contentsOf: chunk)
            }
            return hasher.finalize().map { String(format: "%02x", $0) }.joined()
        }.value
    }

    private static func displayName(_ url: URL) -> String {
        let name = url.deletingPathExtension().lastPathComponent
        return name.isEmpty ? "Sin título" : name
    }
}

private extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}
#endif
