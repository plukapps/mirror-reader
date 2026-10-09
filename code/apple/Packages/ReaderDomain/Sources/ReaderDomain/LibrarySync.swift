import Foundation

/// Resultado de una pasada de `LibrarySync`.
public enum LibrarySyncOutcome: Equatable, Sendable {
    /// Sin sesión y sin cuenta de desarrollo (o falló el inicio): la app sigue solo en local (ADR 0002).
    case noSession
    /// La nube no respondió: no cambió nada local.
    case offline
    /// Cuántos libros vio en la nube, cuántas posiciones guardó y cuántas portadas bajó.
    case synced(books: Int, positions: Int, covers: Int)
    case failed(String)
}

/// Trae a la base local lo que el usuario tiene en la nube: libros como "solo en la nube" (LIB-007), posiciones
/// de lectura más nuevas que las locales (SYN-002, SYN-003) y portadas que faltan (LIB-012). Solo baja: Apple
/// todavía no importa ni lee, así que no tiene nada propio que subir.
///
/// Sin sesión, inicia la de la cuenta de desarrollo si está configurada (K-052). Sin conexión no cambia nada
/// (ADR 0002). Las posiciones y las portadas son secundarias: un fallo en ellas no deshace lo anterior.
public struct LibrarySync: Sendable {
    private let account: AccountRepository
    private let credentials: DevCredentials
    private let remote: RemoteLibrary
    private let positions: RemotePositions
    private let covers: CoverStore
    private let cloud: CloudBooksRepository

    public init(
        account: AccountRepository,
        credentials: DevCredentials,
        remote: RemoteLibrary,
        positions: RemotePositions,
        covers: CoverStore,
        cloud: CloudBooksRepository
    ) {
        self.account = account
        self.credentials = credentials
        self.remote = remote
        self.positions = positions
        self.covers = covers
        self.cloud = cloud
    }

    /// - Parameter onChange: se llama cada vez que la base local cambió (tras libros y posiciones, y tras las
    ///   portadas), para que la pantalla no espere a que bajen todas las portadas.
    public func run(onChange: @Sendable () async -> Void = {}) async -> LibrarySyncOutcome {
        guard await ensureSession() else { return .noSession }
        let books: [RemoteBook]
        do {
            books = try await remote.listBooks()
            try await cloud.addCloudOnly(books)
        } catch is RemoteUnavailableError {
            return .offline
        } catch {
            return .failed(String(describing: error))
        }
        let savedPositions = await syncPositions()
        await onChange()
        let downloadedCovers = await syncCovers()
        if downloadedCovers > 0 { await onChange() }
        return .synced(books: books.count, positions: savedPositions, covers: downloadedCovers)
    }

    private func ensureSession() async -> Bool {
        if await account.currentUser() != nil { return true }
        guard credentials.isConfigured else { return false }
        return (try? await account.signIn(email: credentials.email, password: credentials.password)) != nil
    }

    private func syncPositions() async -> Int {
        do {
            return try await cloud.applyRemotePositions(try await positions.listPositions())
        } catch {
            return 0
        }
    }

    private func syncCovers() async -> Int {
        guard let missing = try? await cloud.booksWithoutCover() else { return 0 }
        var downloaded = 0
        for bookId in missing {
            let temp = cloud.newTempFile()
            do {
                if try await covers.downloadCover(bookId: bookId, to: temp) {
                    try await cloud.installCover(bookId: bookId, from: temp)
                    downloaded += 1
                }
            } catch {
                // Una portada que falla no corta las demás.
            }
            try? FileManager.default.removeItem(at: temp)
        }
        return downloaded
    }
}
