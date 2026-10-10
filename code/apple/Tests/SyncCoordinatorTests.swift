import Foundation
import ReaderDomain
import Testing
@testable import Reader

// La sincronización conectada a las pantallas (plan `2026-10-10-ios-sync.md`, K-150).

/// Pasada falsa: cuenta las corridas, avisa un cambio y devuelve el resultado configurado.
private actor FakePass {
    private(set) var runs = 0
    var outcome: LibrarySyncOutcome = .done(SyncReport())

    func set(_ outcome: LibrarySyncOutcome) { self.outcome = outcome }

    func run(onChange: @Sendable () async -> Void) async -> LibrarySyncOutcome {
        runs += 1
        await onChange()
        return outcome
    }
}

private actor MutableLibrary: LibraryRepository {
    var stored: [LibraryBook] = []
    func set(_ books: [LibraryBook]) { stored = books }
    func books() async -> [LibraryBook] { stored }
}

private func book(_ id: String, uploaded: Bool = true, downloaded: Bool = true) -> LibraryBook {
    LibraryBook(id: id, title: id, author: nil, coverPath: nil, progressPercent: nil, isDownloaded: downloaded, isUploaded: uploaded)
}

/// Espera a que el pedido lanzado en una `Task` llegue al orquestador y termine.
@MainActor
private func settle(_ coordinator: SyncCoordinator) async {
    try? await Task.sleep(for: .milliseconds(50))
    await coordinator.idle()
}

@MainActor
struct SyncCoordinatorTests {
    @Test("SYN-001: sin Firebase no hay estado ni pasadas")
    func localOnly() async {
        let coordinator = SyncCoordinator(pass: nil) {}
        coordinator.request()
        await settle(coordinator)
        #expect(coordinator.state == nil)
    }

    @Test("SYN-001: cada cambio de la pasada y su final vuelven a leer la base")
    func reloadsOnChange() async {
        let pass = FakePass()
        var reloads = 0
        let coordinator = SyncCoordinator(pass: { await pass.run(onChange: $0) }) { reloads += 1 }
        coordinator.request()
        await settle(coordinator)
        // Uno por el cambio y otro al terminar.
        #expect(reloads == 2)
        #expect(coordinator.state == SyncState())
    }

    @Test("SYN-001: volver a la app enseguida no repite la pasada")
    func appBecameActiveRespectsTtl() async {
        let pass = FakePass()
        let coordinator = SyncCoordinator(pass: { await pass.run(onChange: $0) }) {}
        coordinator.request()
        await settle(coordinator)
        coordinator.appBecameActive()
        await settle(coordinator)
        #expect(await pass.runs == 1)
    }

    @Test("SYN-008: la biblioteca muestra el problema de la última pasada y tocarlo reintenta")
    func libraryShowsIssueAndRetries() async {
        let pass = FakePass()
        await pass.set(.done(SyncReport(unreachable: true)))
        let library = MutableLibrary()
        var viewModel: LibraryViewModel?
        let coordinator = SyncCoordinator(pass: { await pass.run(onChange: $0) }) { await viewModel?.load() }
        viewModel = LibraryViewModel(library: library, sync: coordinator)
        coordinator.request()
        await settle(coordinator)

        let status = viewModel?.syncStatus
        #expect(status?.canRetry == true)
        #expect(status?.text == "Sin conexión con la nube. Tus libros siguen aquí y se sincronizarán más tarde. Tocá para reintentar.")

        await pass.set(.done(SyncReport()))
        viewModel?.retrySync()
        await settle(coordinator)
        #expect(await pass.runs == 2)
        #expect(viewModel?.syncStatus == nil)
    }

    @Test("SYN-008: con todo al día, la biblioteca cuenta los libros sin subir")
    func libraryCountsPendingUploads() async {
        let library = MutableLibrary()
        await library.set([book("a", uploaded: false), book("b", uploaded: false), book("c"), book("d", downloaded: false)])
        let coordinator = SyncCoordinator(pass: { _ in .done(SyncReport()) }) {}
        let viewModel = LibraryViewModel(library: library, sync: coordinator)
        await viewModel.load()
        #expect(viewModel.syncStatus?.text == "2 libros sin subir")
        #expect(viewModel.syncStatus?.canRetry == false)
    }
}

struct SyncStatusLineTests {
    @Test("SYN-008: los textos del estado, como Android")
    func texts() {
        #expect(SyncStatusLine(state: SyncState(), pendingUploads: 0) == nil)
        #expect(SyncStatusLine(state: SyncState(hasSession: false), pendingUploads: 3) == nil)
        #expect(SyncStatusLine(state: SyncState(running: true, issue: .offline), pendingUploads: 0)?.text == "Sincronizando…")
        #expect(SyncStatusLine(state: SyncState(), pendingUploads: 1)?.text == "1 libro sin subir")
        #expect(SyncStatusLine(state: SyncState(issue: .notEnoughSpace(1)), pendingUploads: 1)?.text
            == "1 libro no entra en tu espacio. Podés seguir leyendo. Tocá para reintentar.")
        #expect(SyncStatusLine(state: SyncState(issue: .failed(2)), pendingUploads: 0)?.text
            == "No se pudieron sincronizar 2 libros. Tocá para reintentar.")
    }
}
