import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Uso sin cuenta en Inicio y la Biblioteca (GST-010, GST-023, GST-025, GST-026, HOM-012).

private struct Books: LibraryRepository {
    let stored: [LibraryBook]
    func books() async -> [LibraryBook] { stored }
}

private struct Session: AccountRepository {
    let user: AccountUser?
    func currentUser() async -> AccountUser? { user }
}

/// GST-010: cuándo se cerró el aviso, en memoria.
final class MemoryGuestNotice: GuestNoticePreference, @unchecked Sendable {
    private(set) var date: Date?
    func dismissedAt() -> Date? { date }
    func dismiss(at date: Date) { self.date = date }
}

/// Remote Config falso: el banner B y los días.
private actor FlagSource: RemoteFlagSource {
    let noBackup: Bool
    let days: Int?
    init(noBackup: Bool, days: Int? = nil) {
        self.noBackup = noBackup
        self.days = days
    }
    func setPlan(_ plan: String) async throws {}
    func activate() async throws {}
    func fetchAndActivate() async throws {}
    func value(_ key: String) async -> Bool? { key == Feature.guestBannerNoBackup.key ? noBackup : nil }
    func number(_ key: String) async -> Int? { key == Setting.guestNoticeSnoozeDays.key ? days : nil }
    func plans() async -> [Plan]? { nil }
    nonisolated func updates() -> AsyncStream<Void> { AsyncStream { _ in } }
}

private let book = LibraryBook(id: "a", title: "Walden", author: nil, coverPath: nil, progressPercent: nil, lastReadAt: nil)
private let ana = AccountUser(id: "u1", email: "ana@mail.com", displayName: "Ana Ruiz")

private func flags(noBackup: Bool, days: Int? = nil) async -> FeatureFlags {
    let flags = FeatureFlags(source: FlagSource(noBackup: noBackup, days: days), catalog: PlanCatalogStore())
    await flags.startListening()
    // Que se aplique lo bajado.
    for _ in 0..<20 { await Task.yield() }
    try? await Task.sleep(for: .milliseconds(50))
    return flags
}

@MainActor
struct GuestTests {
    @Test("GST-023: sin cuenta y con libros, Inicio muestra el banner A y el avatar sin inicial")
    func bannerAWithoutAccount() async {
        let home = HomeViewModel(
            library: Books(stored: [book]),
            guest: GuestContext(account: Session(user: nil), notice: MemoryGuestNotice(), flags: nil)
        )
        await home.reload()
        #expect(home.guestBanner == .allDevices)
        #expect(home.avatarInitial == nil)
    }

    @Test("GST-025: con el flag, la opción B")
    func bannerBFromTheFlag() async {
        let home = HomeViewModel(
            library: Books(stored: [book]),
            guest: GuestContext(account: Session(user: nil), notice: MemoryGuestNotice(), flags: await flags(noBackup: true))
        )
        await home.reload()
        #expect(home.guestBanner == .noBackup)
    }

    @Test("HOM-012, GST-010: con sesión, la inicial y ningún banner; sin libros, tampoco")
    func signedInOrEmpty() async {
        let signed = HomeViewModel(
            library: Books(stored: [book]),
            guest: GuestContext(account: Session(user: ana), notice: MemoryGuestNotice(), flags: nil)
        )
        await signed.reload()
        #expect(signed.avatarInitial == "A")
        #expect(signed.guestBanner == nil)
        let empty = HomeViewModel(
            library: Books(stored: []),
            guest: GuestContext(account: Session(user: nil), notice: MemoryGuestNotice(), flags: nil)
        )
        await empty.reload()
        #expect(empty.guestBanner == nil)
    }

    @Test("GST-010, GST-026: la cruz lo esconde en Inicio y en la Biblioteca; vuelve pasados los días")
    func dismissAndComeBack() async {
        let notice = MemoryGuestNotice()
        let start = Date(timeIntervalSince1970: 1_000_000)
        func context(at date: Date) -> GuestContext {
            GuestContext(account: Session(user: nil), notice: notice, flags: nil, now: { date })
        }
        let home = HomeViewModel(library: Books(stored: [book]), guest: context(at: start))
        await home.reload()
        home.dismissGuestNotice()
        #expect(home.guestBanner == nil)
        await home.reload()
        #expect(home.guestBanner == nil)

        let library = LibraryViewModel(library: Books(stored: [book]), isSignedIn: { false }, guest: context(at: start))
        await library.load()
        #expect(!library.guestNotice)

        let later = LibraryViewModel(
            library: Books(stored: [book]), isSignedIn: { false }, guest: context(at: start.addingTimeInterval(7 * 86_400))
        )
        await later.load()
        #expect(later.guestNotice)
    }

    @Test("GST-026: los días vienen de Remote Config")
    func snoozeDaysFromRemoteConfig() async {
        let notice = MemoryGuestNotice()
        let now = Date(timeIntervalSince1970: 1_000_000)
        notice.dismiss(at: now.addingTimeInterval(-2 * 86_400))
        let library = LibraryViewModel(
            library: Books(stored: [book]), isSignedIn: { false },
            guest: GuestContext(account: Session(user: nil), notice: notice, flags: await flags(noBackup: false, days: 1), now: { now })
        )
        await library.load()
        #expect(library.guestNotice)
    }
}
