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

private let ana = AccountUser(id: "u1", email: "ana.ruiz@gmail.com", displayName: "ana Ruiz")
private let used = StubQuota(result: .success(StorageQuota(usedBytes: gib * 12 / 10, quotaBytes: 2 * gib)))
private let offline = StubQuota(result: .failure(RemoteUnavailableError("sin red")))

@MainActor
private func loaded(
    _ user: AccountUser?, quota: QuotaSource? = used, cache: MemoryQuotaCache = MemoryQuotaCache()
) async -> ProfileViewModel {
    let viewModel = ProfileViewModel(account: StubAccount(user: user), quota: quota, cache: cache, deviceName: "iPhone")
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
        let viewModel = ProfileViewModel(account: nil, quota: nil, cache: MemoryQuotaCache(), deviceName: "iPhone")
        await viewModel.load()
        #expect(viewModel.email == nil)
        #expect(viewModel.storageFailed)
    }

    @Test("PRF-007: este dispositivo con su nombre")
    func device() async {
        #expect(await loaded(ana).deviceName == "iPhone")
    }
}
