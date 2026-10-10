#if os(iOS)
import Foundation
import ReaderDomain
@preconcurrency import ReadiumShared
@preconcurrency import ReadiumStreamer

/// Abre un EPUB del dispositivo con Readium (ADR 0013), como `PublicationLoader` de Android. Los errores son
/// `BookOpenError` con mensaje apto para el usuario.
@MainActor
final class PublicationLoader {
    private let assetRetriever: AssetRetriever
    private let opener: PublicationOpener

    init() {
        let httpClient = DefaultHTTPClient()
        assetRetriever = AssetRetriever(httpClient: httpClient)
        opener = PublicationOpener(
            parser: DefaultPublicationParser(
                httpClient: httpClient,
                assetRetriever: assetRetriever,
                pdfFactory: DefaultPDFDocumentFactory()
            )
        )
    }

    func open(_ file: URL) async throws -> Publication {
        guard FileManager.default.fileExists(atPath: file.path) else {
            throw BookOpenError("El libro ya no está en el dispositivo.")
        }
        guard let url = file.anyURL.absoluteURL else { throw BookOpenError("No se pudo acceder al archivo.") }
        let asset: Asset
        switch await assetRetriever.retrieve(url: url) {
        case let .success(retrieved): asset = retrieved
        case .failure(.formatNotSupported): throw Self.unreadable
        case .failure: throw BookOpenError("No se pudo leer el archivo.")
        }
        switch await opener.open(asset: asset, allowUserInteraction: false) {
        case let .success(publication):
            // Solo EPUB reflowable sin DRM (LIB-002); el resto se rechaza igual que en Android.
            guard publication.conforms(to: .epub), !publication.isRestricted else {
                publication.close()
                throw Self.unreadable
            }
            return publication
        case .failure:
            throw Self.unreadable
        }
    }

    private static let unreadable = BookOpenError("No se pudo abrir el libro. El archivo puede estar dañado o protegido con DRM.")
}
#endif
