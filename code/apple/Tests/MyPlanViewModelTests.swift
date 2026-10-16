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
    @Test("PLN-012: muestra el plan Free y el espacio guardado, y lo actualiza con el del servidor")
    func cachedThenFresh() async {
        let cache = PlansMemoryQuotaCache(StorageQuota(usedBytes: 2 * mib, quotaBytes: 15 * mib))
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib)), cache: cache)
        #expect(viewModel.planName == "Free")
        #expect(viewModel.storageText == "2 MB de 15 MB")
        await viewModel.load()
        #expect(viewModel.storageText == "12 MB de 15 MB")
        #expect(viewModel.usage?.fraction == 0.8)
        #expect(cache.saved?.usedBytes == 12 * mib)
        #expect(viewModel.plan == .free)
        #expect(viewModel.maxFile == "2 MB")
        #expect(viewModel.offer == MyPlanOffer(title: "Con Plus: 5 GB en la nube", body: "Archivos de hasta 50 MB, temas y fuentes."))
    }

    @Test("PLN-017, ACC-010: en Pro, su nombre y el máximo del servidor, sin oferta")
    func proPlan() async {
        let quota = StorageQuota(usedBytes: 3 * mib, quotaBytes: 50 * 1024 * mib, plan: "pro", maxFileBytes: 100 * mib)
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: quota), cache: PlansMemoryQuotaCache())
        await viewModel.load()
        #expect(viewModel.plan == .pro)
        #expect(viewModel.planName == "Pro")
        #expect(viewModel.maxFile == "100 MB")
        #expect(viewModel.offer == nil)
    }

    @Test("PLN-017: en Plus, la oferta de Pro con sus valores por defecto")
    func plusPlan() async {
        let quota = StorageQuota(usedBytes: 0, quotaBytes: 5 * 1024 * mib, plan: "plus")
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: quota), cache: PlansMemoryQuotaCache())
        await viewModel.load()
        #expect(viewModel.planName == "Plus")
        #expect(viewModel.maxFile == "50 MB")
        #expect(viewModel.offer == MyPlanOffer(title: "Con Pro: 50 GB en la nube", body: "Archivos de hasta 100 MB."))
    }

    @Test("PLN-012, ACC-010: en Free también vale el máximo del servidor")
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

    @Test("ACC-007, ACC-008: sin el máximo del servidor, el del plan en el catálogo vigente, y se actualiza si cambia")
    func followsCatalog() async {
        let store = PlanCatalogStore()
        let quota = StorageQuota(usedBytes: 0, quotaBytes: 5 * 1024 * mib, plan: "plus")
        let viewModel = MyPlanViewModel(quota: FixedQuota(result: quota), cache: PlansMemoryQuotaCache(), catalog: store)
        await viewModel.load()
        #expect(viewModel.maxFile == "50 MB")
        let following = Task { await viewModel.followCatalog() }
        defer { following.cancel() }
        let plus = Plan(id: .plus, storageBytes: 5 * 1024 * mib, maxFileBytes: 7 * mib, monthlyPrice: 2.99, annualPrice: 24.99, currency: "USD")
        let pro = Plan(id: .pro, storageBytes: 80 * 1024 * mib, maxFileBytes: 200 * mib, monthlyPrice: 5.99, annualPrice: 49.99, currency: "USD")
        store.update([DefaultPlans.free, plus, pro])
        for _ in 0..<2_000 where viewModel.plans != store.plans { await Task.yield() }
        #expect(viewModel.maxFile == "7 MB")
        #expect(viewModel.offer == MyPlanOffer(title: "Con Pro: 80 GB en la nube", body: "Archivos de hasta 200 MB."))
    }
}
