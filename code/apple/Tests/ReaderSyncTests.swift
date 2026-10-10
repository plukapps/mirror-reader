import Foundation
import ReaderDomain
import Testing
@testable import Reader

// El lector con la posición de la nube (plan `2026-10-10-ios-sync.md`, K-153), como `ReaderViewModelTest` de Android
// (SYN-003, SYN-011, SYN-013).

@MainActor
private final class FakeSource: ReaderBookSource {
    private(set) var opened = 0
    func open(bookId: String) async throws -> OpenedBook {
        opened += 1
        return OpenedBook(title: "Walden", toc: [], readingOrder: ["c1.xhtml"], initialLocatorJson: nil)
    }
}

private actor FakePositions: ReadingPositionRepository {
    private(set) var saved: [SavedPosition] = []
    func position(bookId: String) async -> SavedPosition? { saved.last }
    func save(bookId: String, position: SavedPosition) async { saved.append(position) }
}

private final class FakeSettings: ReaderSettingsRepository, @unchecked Sendable {
    func load() -> ReaderSettings { ReaderSettings() }
    func save(_ settings: ReaderSettings) {}
}

/// La nube vista desde el lector.
private actor FakeReaderSync: ReaderPositionSync {
    var opening: OpeningPosition = .local
    private(set) var calls: [String] = []
    private var continuation: AsyncStream<RemotePosition>.Continuation?

    init(opening: OpeningPosition = .local) { self.opening = opening }

    func resolve(bookId: String) async -> OpeningPosition {
        calls.append("consultar")
        return opening
    }

    func accept(_ remote: RemotePosition) async { calls.append("aceptar") }
    func decline(_ local: ReadingPosition) async { calls.append("rechazar") }

    func remoteUpdates() async -> AsyncStream<RemotePosition> {
        let (stream, continuation) = AsyncStream<RemotePosition>.makeStream()
        self.continuation = continuation
        return stream
    }

    nonisolated func sendPending() { Task { await record("enviar") } }

    func record(_ call: String) { calls.append(call) }
    func emit(_ remote: RemotePosition) { continuation?.yield(remote) }
    var isListening: Bool { continuation != nil }
}

private func remote(_ bookId: String = "a", progress: Double?, locator: String = "{\"href\":\"c9.xhtml\"}") -> RemotePosition {
    RemotePosition(
        position: ReadingPosition(bookId: bookId, locatorJson: locator, progress: progress, readAt: 50),
        deviceId: "otro",
        deviceName: "SM-X510"
    )
}

@MainActor
struct ReaderSyncTests {
    private let positions = FakePositions()

    private func makeViewModel(_ source: FakeSource, sync: FakeReaderSync) -> ReaderViewModel {
        ReaderViewModel(bookId: "a", source: source, positions: positions, settings: FakeSettings(), saveDelay: .zero, sync: sync)
    }

    @Test("SYN-012: antes de mostrar el libro se consulta su posición en la nube")
    func resolvesBeforeOpening() async {
        let source = FakeSource()
        let sync = FakeReaderSync(opening: .updated)
        let viewModel = makeViewModel(source, sync: sync)
        await viewModel.load()
        #expect(await sync.calls == ["consultar"])
        #expect(source.opened == 1)
        if case .ready = viewModel.phase {} else { Issue.record("debía quedar listo") }
    }

    @Test("SYN-003: con un salto grande pregunta antes de abrir")
    func bigJumpAsks() async {
        let source = FakeSource()
        let local = ReadingPosition(bookId: "a", locatorJson: "local", progress: 0.1, readAt: 10)
        let sync = FakeReaderSync(opening: .confirm(remote: remote(progress: 0.6), local: local))
        let viewModel = makeViewModel(source, sync: sync)
        await viewModel.load()
        #expect(viewModel.phase == .confirming(deviceName: "SM-X510", remotePercent: 60))
        #expect(source.opened == 0)
    }

    @Test("SYN-003: continuar allí aplica la posición de la nube y abre")
    func acceptOpens() async {
        let source = FakeSource()
        let local = ReadingPosition(bookId: "a", locatorJson: "local", progress: 0.1, readAt: 10)
        let sync = FakeReaderSync(opening: .confirm(remote: remote(progress: 0.6), local: local))
        let viewModel = makeViewModel(source, sync: sync)
        await viewModel.load()
        await viewModel.resumeChoice(useRemote: true)
        #expect(await sync.calls == ["consultar", "aceptar"])
        #expect(source.opened == 1)
    }

    @Test("SYN-003: quedarme aquí guarda la local como la más reciente y abre")
    func declineOpens() async {
        let source = FakeSource()
        let local = ReadingPosition(bookId: "a", locatorJson: "local", progress: 0.1, readAt: 10)
        let sync = FakeReaderSync(opening: .confirm(remote: remote(progress: 0.6), local: local))
        let viewModel = makeViewModel(source, sync: sync)
        await viewModel.load()
        await viewModel.resumeChoice(useRemote: false)
        #expect(await sync.calls == ["consultar", "rechazar"])
        #expect(source.opened == 1)
    }

    @Test("SYN-013: si otro dispositivo avanza el libro, se ofrece seguir desde allí sin mover la página")
    func offersContinueFrom() async {
        let sync = FakeReaderSync()
        let viewModel = makeViewModel(FakeSource(), sync: sync)
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.30, position: 3, href: "c1.xhtml")
        #expect(await eventually { await sync.isListening })

        await sync.emit(remote("otro-libro", progress: 0.9))
        await sync.emit(remote(progress: 0.60))

        #expect(await eventually { viewModel.continueFrom != nil })
        #expect(viewModel.continueFrom == ReaderViewModel.ContinueFrom(deviceName: "SM-X510", percent: 60))
        #expect(viewModel.jump == nil)
    }

    @Test("SYN-013: una lectura remota que no va más adelante no se ofrece")
    func behindIsNotOffered() async {
        let sync = FakeReaderSync()
        let viewModel = makeViewModel(FakeSource(), sync: sync)
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.50, position: 3, href: "c1.xhtml")
        #expect(await eventually { await sync.isListening })
        await sync.emit(remote(progress: 0.502))
        try? await Task.sleep(for: .milliseconds(30))
        #expect(viewModel.continueFrom == nil)
    }

    @Test("SYN-013: tocar el aviso navega a la posición del otro dispositivo")
    func continueNavigates() async {
        let sync = FakeReaderSync()
        let viewModel = makeViewModel(FakeSource(), sync: sync)
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.30, position: 3, href: "c1.xhtml")
        #expect(await eventually { await sync.isListening })
        await sync.emit(remote(progress: 0.60, locator: "remota"))
        #expect(await eventually { viewModel.continueFrom != nil })

        viewModel.continueFromOtherDevice()

        #expect(viewModel.continueFrom == nil)
        #expect(viewModel.jump?.locatorJson == "remota")
    }

    @Test("SYN-013: el aviso se retira solo cuando la lectura de aquí lo alcanza, o al descartarlo")
    func noticeGoesAway() async {
        let sync = FakeReaderSync()
        let viewModel = makeViewModel(FakeSource(), sync: sync)
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.30, position: 3, href: "c1.xhtml")
        #expect(await eventually { await sync.isListening })
        await sync.emit(remote(progress: 0.60))
        #expect(await eventually { viewModel.continueFrom != nil })

        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.40, position: 4, href: "c1.xhtml")
        #expect(viewModel.continueFrom != nil)
        viewModel.onLocationChanged(locatorJson: "{}", totalProgression: 0.60, position: 6, href: "c1.xhtml")
        #expect(viewModel.continueFrom == nil)

        await sync.emit(remote(progress: 0.90))
        #expect(await eventually { viewModel.continueFrom != nil })
        viewModel.dismissContinueFrom()
        #expect(viewModel.continueFrom == nil)
    }

    @Test("SYN-011: al cerrar el libro se guarda la posición y recién entonces se envía")
    func closeSavesThenSends() async {
        let sync = FakeReaderSync()
        let viewModel = ReaderViewModel(
            bookId: "a", source: FakeSource(), positions: positions, settings: FakeSettings(), saveDelay: .seconds(60), sync: sync
        )
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "{\"p\":1}", totalProgression: 0.3, position: 3, href: "c1.xhtml")

        await viewModel.close()

        #expect(await positions.saved == [SavedPosition(locatorJson: "{\"p\":1}", progress: 0.3)])
        #expect(await eventually { await sync.calls.last == "enviar" })
    }

    @Test("SYN-011: al pasar a segundo plano se guarda la posición y se envía, sin cerrar el libro")
    func backgroundSavesAndSends() async {
        let sync = FakeReaderSync()
        let viewModel = ReaderViewModel(
            bookId: "a", source: FakeSource(), positions: positions, settings: FakeSettings(), saveDelay: .seconds(60), sync: sync
        )
        await viewModel.load()
        viewModel.onLocationChanged(locatorJson: "{\"p\":2}", totalProgression: 0.4, position: 4, href: "c1.xhtml")

        await viewModel.wentToBackground()

        #expect(await positions.saved == [SavedPosition(locatorJson: "{\"p\":2}", progress: 0.4)])
        #expect(await eventually { await sync.calls.last == "enviar" })
        if case .ready = viewModel.phase {} else { Issue.record("el libro sigue abierto") }
    }
}

/// Espera hasta que `condition` se cumpla, como mucho un segundo.
@MainActor
private func eventually(_ condition: @MainActor () async -> Bool) async -> Bool {
    for _ in 0..<200 {
        if await condition() { return true }
        try? await Task.sleep(for: .milliseconds(5))
    }
    return await condition()
}
