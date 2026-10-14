import Foundation
import ReaderDomain
import Testing
@testable import Reader

private let gib: Int64 = 1024 * 1024 * 1024

private struct StubAccount: AccountRepository {
    let user: AccountUser?
    func currentUser() async -> AccountUser? { user }
}

private struct StubQuota: QuotaSource {
    let result: Result<StorageQuota, Error>
    func current() async throws -> StorageQuota { try result.get() }
}

private final class MemoryQuotaCache: QuotaCache, @unchecked Sendable {
    var saved: StorageQuota?
    init(_ saved: StorageQuota? = nil) { self.saved = saved }
    func last() -> StorageQuota? { saved }
    func save(_ quota: StorageQuota) { saved = quota }
}

private final class MemorySettings: ReaderSettingsRepository, @unchecked Sendable {
    var stored: ReaderSettings
    init(_ stored: ReaderSettings = ReaderSettings()) { self.stored = stored }
    func load() -> ReaderSettings { stored }
    func save(_ settings: ReaderSettings) { stored = settings }
}

private struct NoPasswords: PasswordRepository {
    func changePassword(current: String, new: String) async throws {}
}

private struct IdleSync: SyncControl {
    func stopListening() async {}
    func whileIdle<T: Sendable>(_ body: @Sendable () async -> T) async -> T { await body() }
    func startListening() async {}
}

private final class Wipe: LocalAccountData, @unchecked Sendable {
    var wiped = 0
    func wipe() async throws { wiped += 1 }
}

private struct NoReminders: ReminderScheduler {
    func requestPermission() async -> Bool { true }
    func schedule(_ reminder: DailyReminder, goal: ReadingGoal?) async {}
    func cancel() async {}
}

private final class Session: SessionRepository, @unchecked Sendable {
    var fails = false
    var signOuts = 0
    func signOut() async throws {
        signOuts += 1
        if fails { throw AuthFailure(.network) }
    }
}

private func signOutUseCase(_ session: Session, wipe: Wipe = Wipe(), pending: Bool = false) -> SignOut {
    SignOut(sync: IdleSync(), localData: wipe, session: session, reminders: NoReminders(), hasPending: { pending })
}

private let ana = AccountUser(id: "u1", email: "ana.ruiz@gmail.com", displayName: "ana Ruiz")
private let used = StubQuota(result: .success(StorageQuota(usedBytes: gib * 12 / 10, quotaBytes: 2 * gib)))
private let offline = StubQuota(result: .failure(RemoteUnavailableError("sin red")))

@MainActor
private func loaded(
    _ user: AccountUser?, quota: QuotaSource? = used, cache: MemoryQuotaCache = MemoryQuotaCache(),
    settings: MemorySettings = MemorySettings(), signOut: SignOut? = nil, plan: PlanId? = nil
) async -> ProfileViewModel {
    var planSource: (@MainActor () -> PlanId)?
    if let plan { planSource = { plan } }
    let viewModel = ProfileViewModel(
        account: StubAccount(user: user), quota: quota, cache: cache, deviceName: "iPhone", settings: settings,
        signOut: signOut, plan: planSource
    )
    await viewModel.load()
    return viewModel
}

@MainActor
struct ProfileViewModelTests {
    @Test("PRF-005: nombre, email e inicial del nombre")
    func header() async {
        let viewModel = await loaded(ana)
        #expect(viewModel.name == "ana Ruiz")
        #expect(viewModel.email == "ana.ruiz@gmail.com")
        #expect(viewModel.initial == "A")
    }

    @Test("PRF-005: sin nombre, la inicial sale del email")
    func headerWithoutName() async {
        let viewModel = await loaded(AccountUser(id: "u1", email: "luis@mail.com"))
        #expect(viewModel.name == nil)
        #expect(viewModel.initial == "L")
    }

    @Test("PRF-008: cambiar la contraseña solo con email y contraseña")
    func changePassword() async {
        #expect(await loaded(AccountUser(id: "u", email: "a@mail.com", signInMethod: .password)).canChangePassword)
        #expect(await !loaded(AccountUser(id: "u", email: "a@gmail.com", signInMethod: .google)).canChangePassword)
        #expect(await !loaded(AccountUser(id: "u", email: "a@icloud.com", signInMethod: .other)).canChangePassword)
    }

    @Test("PRF-020: la pantalla de cambiar la contraseña existe solo con cuenta")
    func changePasswordScreen() async {
        #expect(await loaded(ana).makeChangePassword() == nil)
        let viewModel = ProfileViewModel(
            account: StubAccount(user: ana), quota: used, cache: MemoryQuotaCache(), deviceName: "iPhone",
            settings: MemorySettings(), passwords: NoPasswords()
        )
        #expect(viewModel.makeChangePassword() != nil)
    }

    @Test("PRF-021: Perfil muestra la apariencia elegida y la cambia")
    func appearance() async {
        let appearance = AppearanceSettings(repository: UserDefaultsAppearance(defaults: UserDefaults(suiteName: "ProfileAppearance-\(UUID().uuidString)")!))
        let viewModel = ProfileViewModel(
            account: StubAccount(user: ana), quota: used, cache: MemoryQuotaCache(), deviceName: "iPhone",
            settings: MemorySettings(), appearance: appearance
        )
        #expect(viewModel.appearance == .system)
        viewModel.setAppearance(.light)
        #expect(viewModel.appearance == .light)
        #expect(appearance.appearance == .light)
    }

    @Test("PRF-006: espacio en la unidad del total, con la proporción usada")
    func storage() async throws {
        let viewModel = await loaded(ana)
        let storage = try #require(viewModel.storage)
        #expect(storage.used == "1,2 GB")
        #expect(storage.total == "2 GB")
        #expect(storage.free == "0,8 GB")
        #expect(abs(storage.fraction - 0.6) < 0.001)
        #expect(storage.isFreePlan)
        #expect(!viewModel.storageFailed)
    }

    @Test("PRF-006: la proporción no pasa de 1")
    func fractionCapped() async {
        let viewModel = await loaded(ana, quota: StubQuota(result: .success(StorageQuota(usedBytes: 3 * gib, quotaBytes: 2 * gib))))
        #expect(viewModel.storage?.fraction == 1)
        #expect(viewModel.storage?.free == "0 GB")
    }

    @Test("PRF-006: lo leído se guarda; sin conexión se muestra lo guardado")
    func cache() async {
        let cache = MemoryQuotaCache()
        _ = await loaded(ana, cache: cache)
        #expect(cache.saved == StorageQuota(usedBytes: gib * 12 / 10, quotaBytes: 2 * gib))

        let viewModel = await loaded(ana, quota: offline, cache: MemoryQuotaCache(StorageQuota(usedBytes: gib / 2, quotaBytes: 2 * gib)))
        #expect(viewModel.storage?.used == "0,5 GB")
        #expect(!viewModel.storageFailed)
    }

    @Test("PRF-006: si nunca se leyó y falla, se avisa")
    func failureWithoutCache() async {
        let viewModel = await loaded(ana, quota: offline)
        #expect(viewModel.storage == nil)
        #expect(viewModel.storageFailed)
    }

    @Test("PRF-006: sin cuenta configurada (sin Firebase) no hay espacio que leer")
    func withoutBackend() async {
        let viewModel = ProfileViewModel(
            account: nil, quota: nil, cache: MemoryQuotaCache(), deviceName: "iPhone", settings: MemorySettings()
        )
        await viewModel.load()
        #expect(viewModel.email == nil)
        #expect(viewModel.storageFailed)
    }

    @Test("PRF-007: este dispositivo con su nombre")
    func device() async {
        #expect(await loaded(ana).deviceName == "iPhone")
    }

    @Test("PRF-011: Perfil muestra los mismos ajustes que el lector; en Gratis, con Clásico y serifa (PLN-015)")
    func showsReaderSettings() async {
        let saved = ReaderSettings(theme: .sepia, fontScale: 1.2, font: .mono, lineSpacing: .wide)
        let viewModel = await loaded(ana, settings: MemorySettings(saved), plan: .reader)
        #expect(viewModel.reading == saved)
        let free = await loaded(ana, settings: MemorySettings(saved))
        #expect(free.reading == ReaderSettings(theme: .light, fontScale: 1.2, font: .serif, lineSpacing: .wide))
    }

    @Test("PLN-015: en Gratis, tocar Noche o una letra paga no cambia la preferencia y pide la hoja de función paga")
    func freePlanPaidOptions() async {
        let settings = MemorySettings()
        let viewModel = await loaded(ana, settings: settings)
        viewModel.setTheme(.dark)
        #expect(settings.stored.theme == .light)
        #expect(viewModel.sheet == .paid(.themesAndFonts))
        viewModel.openFontSheet()
        #expect(viewModel.sheet == .font)
        viewModel.setFont(.sans)
        #expect(settings.stored.font == .serif)
        #expect(viewModel.sheet == .paid(.themesAndFonts))
        #expect(viewModel.isLocked(ReadingTheme.sepia))
        #expect(!viewModel.isLocked(ReaderFont.serif))
    }

    @Test("PLN-015: el plan sale de la cuota guardada; con Lector las opciones no llevan marca")
    func planFromCache() async {
        let cache = MemoryQuotaCache(StorageQuota(usedBytes: 0, quotaBytes: 5 * gib, plan: "plus"))
        let viewModel = await loaded(ana, quota: nil, cache: cache)
        #expect(!viewModel.isLocked(ReadingTheme.dark))
    }

    @Test("PRF-011, PRF-013 a PRF-015: los cambios se guardan donde los lee el lector")
    func changesAreSaved() async {
        let settings = MemorySettings()
        let viewModel = await loaded(ana, settings: settings, plan: .library)
        viewModel.setTheme(.dark)
        viewModel.setLineSpacing(.wide)
        viewModel.setFont(.sans)
        viewModel.biggerFont()
        viewModel.biggerFont()
        viewModel.smallerFont()
        #expect(settings.stored == ReaderSettings(theme: .dark, fontScale: 1.1, font: .sans, lineSpacing: .wide))
        #expect(viewModel.reading == settings.stored)
    }

    @Test("PRF-011: al volver a Perfil se ven los cambios hechos en el lector")
    func reloadsSettings() async {
        let settings = MemorySettings()
        let viewModel = await loaded(ana, settings: settings, plan: .reader)
        settings.stored = ReaderSettings(theme: .sepia)
        await viewModel.load()
        #expect(viewModel.reading.theme == .sepia)
    }

    @Test("PRF-009: \"Cerrar sesión\" pide confirmación y avisa si hay cambios sin subir")
    func asksForConfirmation() async {
        let session = Session()
        let viewModel = await loaded(ana, signOut: signOutUseCase(session, pending: true))
        await viewModel.requestSignOut()
        #expect(viewModel.signOutDialog == SignOutDialog(hasPendingChanges: true))
        #expect(session.signOuts == 0)
        viewModel.dismissSignOut()
        #expect(viewModel.signOutDialog == nil)
    }

    @Test("PRF-009: confirmar borra lo local y cierra la sesión")
    func confirmSignsOut() async {
        let session = Session()
        let wipe = Wipe()
        let viewModel = await loaded(ana, signOut: signOutUseCase(session, wipe: wipe))
        await viewModel.requestSignOut()
        await viewModel.confirmSignOut()
        #expect(wipe.wiped == 1)
        #expect(session.signOuts == 1)
        #expect(viewModel.signedOut)
        #expect(viewModel.signOutDialog == nil)
    }

    @Test("PRF-009: si no se pudo cerrar la sesión, se avisa y se sigue en Perfil")
    func failedSignOut() async {
        let session = Session()
        session.fails = true
        let viewModel = await loaded(ana, signOut: signOutUseCase(session))
        await viewModel.requestSignOut()
        await viewModel.confirmSignOut()
        #expect(!viewModel.signedOut)
        #expect(viewModel.signOutFailed)
    }

    @Test("PRF-009: sin cuenta (sin Firebase) no se puede cerrar sesión")
    func withoutSignOut() async {
        let viewModel = await loaded(ana)
        #expect(!viewModel.canSignOut)
    }
}
