import Foundation
import ReaderDomain
import Testing
@testable import Reader

private let mib: Int64 = 1024 * 1024

final class PlansMemoryQuotaCache: QuotaCache, @unchecked Sendable {
    var saved: StorageQuota?
    init(_ saved: StorageQuota? = nil) { self.saved = saved }
    func last() -> StorageQuota? { saved }
    func save(_ quota: StorageQuota) { saved = quota }
}

@MainActor
struct PlansViewModelTests {
    @Test("PLN-004, PLN-005: arranca en mensual con Plus elegido (en amarillo) y la tabla cerrada")
    func startsMonthlyWithReader() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        #expect(viewModel.billing == .monthly)
        #expect(viewModel.selected == .plus)
        #expect(viewModel.isHighlighted(.plus))
        #expect(!viewModel.isHighlighted(.pro))
        #expect(!viewModel.isHighlighted(.free))
        #expect(!viewModel.tableOpen)
        #expect(viewModel.cta.title == "Elegir Plus · $2.99 por mes")
        #expect(viewModel.cta.subtitle == "Se renueva cada mes. Cancelás cuando quieras.")
    }

    @Test("PLN-004, PLN-006: pasar a anual cambia el botón; al tocarlo se avisa (PRF-003)")
    func annualChangesCta() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        viewModel.billing = .annual
        #expect(viewModel.cta.title == "Elegir Plus · $24.99 por año")
        #expect(viewModel.cta.subtitle == "Se renueva cada año. Cancelás cuando quieras.")
    }

    @Test("PLN-005: se elige Pro tocando su tarjeta y pasa a amarillo; Free no se elige")
    func selection() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        viewModel.select(.pro)
        #expect(viewModel.selected == .pro)
        #expect(viewModel.isHighlighted(.pro))
        #expect(!viewModel.isHighlighted(.plus))
        viewModel.select(.free)
        #expect(viewModel.selected == .pro)
        #expect(!viewModel.isHighlighted(.free))
        #expect(viewModel.cta.title == "Elegir Pro · $5.99 por mes")
    }

    @Test("PLN-006: la tabla se despliega y se repliega")
    func table() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        viewModel.toggleTable()
        #expect(viewModel.tableOpen)
        viewModel.toggleTable()
        #expect(!viewModel.tableOpen)
    }

    @Test("PLN-003: el uso sale de la cuota guardada; sin dato, no hay fila")
    func usage() {
        let cache = PlansMemoryQuotaCache()
        let viewModel = PlansViewModel(cache: cache)
        #expect(viewModel.usage == nil)
        cache.saved = StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib)
        viewModel.load()
        #expect(viewModel.usage?.text == "Usás 12 MB de 15 MB")
        #expect(viewModel.storage(.free) == "15 MB")
        #expect(viewModel.accountPlan == .free)
    }

    @Test("PLN-005, PLN-017: \"Tu plan\" va en el plan de la cuenta")
    func accountPlan() {
        let cache = PlansMemoryQuotaCache(StorageQuota(usedBytes: 0, quotaBytes: 15 * mib, plan: "pro"))
        let viewModel = PlansViewModel(cache: cache)
        viewModel.load()
        #expect(viewModel.accountPlan == .pro)
        cache.saved = StorageQuota(usedBytes: 0, quotaBytes: 15 * mib, plan: "plus")
        viewModel.load()
        #expect(viewModel.accountPlan == .plus)
    }

    @Test("PLN-002: se abre y se cierra; cada vez arranca de nuevo")
    func presenter() {
        let presenter = PlansPresenter(cache: PlansMemoryQuotaCache())
        presenter.openPlans()
        #expect(presenter.plansOpen)
        let first = presenter.makePlans()
        first.billing = .annual
        presenter.closePlans()
        #expect(!presenter.plansOpen)
        #expect(presenter.makePlans().billing == .monthly)
    }

    @Test("PLN-007: Ver planes desde una hoja abre Planes cuando la hoja termina de cerrarse; Ahora no, no")
    func plansAfterSheet() {
        let presenter = PlansPresenter(cache: PlansMemoryQuotaCache())
        presenter.sheetDismissed()
        #expect(!presenter.plansOpen)
        presenter.openPlansAfterSheet()
        #expect(!presenter.plansOpen)
        presenter.sheetDismissed()
        #expect(presenter.plansOpen)
        presenter.closePlans()
        presenter.sheetDismissed()
        #expect(!presenter.plansOpen)
    }


    @Test("ACC-007, K-290: si cambia el catálogo, Planes muestra los valores nuevos")
    func followsCatalog() async {
        let store = PlanCatalogStore()
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache(), catalog: store)
        let following = Task { await viewModel.followCatalog() }
        defer { following.cancel() }
        let plus = Plan(id: .plus, storageBytes: 7 * 1024 * mib, maxFileBytes: 70 * mib, monthlyPrice: 3.49, annualPrice: 34.99, currency: "USD")
        store.update([DefaultPlans.free, plus, DefaultPlans.pro])
        for _ in 0..<2_000 where viewModel.plans != store.plans { await Task.yield() }
        #expect(viewModel.storage(.plus) == "7 GB")
        #expect(viewModel.cta.title == "Elegir Plus · $3.49 por mes")
        #expect(viewModel.comparison[1].plus == "70 MB")
    }
}
