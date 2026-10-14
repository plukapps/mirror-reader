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
        #expect(viewModel.plan == .free)
        #expect(viewModel.maxFile == "2 MB")
        #expect(viewModel.offer == MyPlanOffer(title: "Con Lector: 5 GB en la nube", body: "Archivos de hasta 50 MB, temas y fuentes."))
    }

    @Test("PLN-017, ACC-010: en Biblioteca, su nombre y el máximo del servidor, sin oferta")
    func proPlan() async {
        let quota = StorageQuota(usedBytes: 3 * mib, quotaBytes: 50 * 1024 * mib, plan: "pro", maxFileBytes: 100 * mib)
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: quota), cache: PlansMemoryQuotaCache())
        await viewModel.load()
        #expect(viewModel.plan == .pro)
        #expect(viewModel.planName == "Biblioteca")
        #expect(viewModel.maxFile == "100 MB")
        #expect(viewModel.offer == nil)
    }

    @Test("PLN-017: en Lector, la oferta de Biblioteca con sus valores por defecto")
    func plusPlan() async {
        let quota = StorageQuota(usedBytes: 0, quotaBytes: 5 * 1024 * mib, plan: "plus")
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: quota), cache: PlansMemoryQuotaCache())
        await viewModel.load()
        #expect(viewModel.planName == "Lector")
        #expect(viewModel.maxFile == "50 MB")
        #expect(viewModel.offer == MyPlanOffer(title: "Con Biblioteca: 50 GB en la nube", body: "Archivos de hasta 100 MB."))
    }

    @Test("PLN-012, ACC-010: en Gratis también vale el máximo del servidor")
    func freePlanUsesServerMaximum() async {
        let quota = StorageQuota(usedBytes: 0, quotaBytes: 15 * mib, maxFileBytes: 3 * mib)
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: quota), cache: PlansMemoryQuotaCache())
        await viewModel.load()
        #expect(viewModel.maxFile == "3 MB")
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
