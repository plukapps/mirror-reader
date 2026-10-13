import Foundation
import ReaderDomain
import Testing
@testable import Reader

private let mib: Int64 = 1024 * 1024

private struct FixedQuota: QuotaSource {
    let result: StorageQuota?
    func current() async throws -> StorageQuota {
        guard let result else { throw RemoteUnavailableError("sin red") }
        return result
    }
}

@MainActor
struct MyPlanViewModelTests {
    @Test("PLN-012: muestra el plan Gratis y el espacio guardado, y lo actualiza con el del servidor")
    func cachedThenFresh() async {
        let cache = PlansMemoryQuotaCache(StorageQuota(usedBytes: 2 * mib, quotaBytes: 15 * mib))
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib)), cache: cache)
        #expect(viewModel.planName == "Gratis")
        #expect(viewModel.storageText == "2 MB de 15 MB")
        await viewModel.load()
        #expect(viewModel.storageText == "12 MB de 15 MB")
        #expect(viewModel.usage?.fraction == 0.8)
        #expect(cache.saved?.usedBytes == 12 * mib)
        #expect(viewModel.readerStorage == "5 GB")
    }

    @Test("PLN-012: sin conexión queda lo guardado; sin nada, no hay espacio que mostrar")
    func offline() async {
        let saved = MyPlanViewModel(
            quota: FixedQuota(result: nil), cache: PlansMemoryQuotaCache(StorageQuota(usedBytes: mib, quotaBytes: 15 * mib))
        )
        await saved.load()
        #expect(saved.storageText == "1 MB de 15 MB")
        let empty = MyPlanViewModel(quota: nil, cache: PlansMemoryQuotaCache())
        await empty.load()
        #expect(empty.storageText == nil)
    }
}
