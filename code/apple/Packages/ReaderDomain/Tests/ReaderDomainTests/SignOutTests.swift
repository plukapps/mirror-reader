import Foundation
import Testing
@testable import ReaderDomain

/// Registro de lo que pasó, en orden.
private final class Events: @unchecked Sendable {
    private let lock = NSLock()
    private var list: [String] = []
    func add(_ event: String) { lock.withLock { list.append(event) } }
    var all: [String] { lock.withLock { list } }
}

private struct Sync: SyncControl {
    let events: Events
    func stopListening() async { events.add("stop") }
    func whileIdle<T: Sendable>(_ body: @Sendable () async -> T) async -> T {
        events.add("idle")
        let value = await body()
        events.add("resume")
        return value
    }
    func startListening() async { events.add("start") }
}

private struct LocalData: LocalAccountData {
    let events: Events
    var fails = false
    func wipe() async throws {
        events.add("wipe")
        if fails { throw CocoaError(.fileWriteNoPermission) }
    }
}

private struct Reminders: ReminderScheduler {
    let events: Events
    func requestPermission() async -> Bool { true }
    func schedule(_ reminder: DailyReminder, goal: ReadingGoal?) async {}
    func cancel() async { events.add("cancelReminder") }
}

private struct Session: SessionRepository {
    let events: Events
    var fails = false
    func signOut() async throws {
        events.add("signOut")
        if fails { throw AuthFailure(.network) }
    }
}

private func signOut(_ events: Events, wipeFails: Bool = false, signOutFails: Bool = false, pending: Bool = false) -> SignOut {
    SignOut(
        sync: Sync(events: events),
        localData: LocalData(events: events, fails: wipeFails),
        session: Session(events: events, fails: signOutFails),
        reminders: Reminders(events: events),
        hasPending: { pending }
    )
}

struct SignOutTests {
    @Test("PRF-009: deja de escuchar, espera a la pasada, borra, cierra la sesión y vuelve a escuchar")
    func order() async throws {
        let events = Events()
        try await signOut(events)()
        #expect(events.all == ["stop", "idle", "wipe", "cancelReminder", "signOut", "resume", "start"])
    }

    @Test("PRF-009: si el borrado falla, igual se cierra la sesión")
    func wipeFailure() async throws {
        let events = Events()
        try await signOut(events, wipeFails: true)()
        #expect(events.all.contains("signOut"))
    }

    @Test("PRF-009: si cerrar la sesión falla, se informa")
    func signOutFailure() async {
        await #expect(throws: AuthFailure.self) { try await signOut(Events(), signOutFails: true)() }
    }

    @Test("PRF-009: la confirmación sabe si hay cambios sin subir")
    func pending() async {
        #expect(await signOut(Events(), pending: true).hasPendingChanges())
        #expect(await !signOut(Events(), pending: false).hasPendingChanges())
    }
}
