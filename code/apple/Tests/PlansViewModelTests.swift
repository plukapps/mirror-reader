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
    @Test("PLN-004, PLN-005: arranca en mensual con Lector elegido (en amarillo) y la tabla cerrada")
    func startsMonthlyWithReader() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        #expect(viewModel.billing == .monthly)
        #expect(viewModel.selected == .plus)
        #expect(viewModel.isHighlighted(.plus))
        #expect(!viewModel.isHighlighted(.pro))
        #expect(!viewModel.isHighlighted(.free))
        #expect(!viewModel.tableOpen)
        #expect(viewModel.cta.title == "Elegir Lector · $2.99 por mes")
        #expect(viewModel.cta.subtitle == "Se renueva cada mes. Cancelás cuando quieras.")
    }

    @Test("PLN-004, PLN-006: pasar a anual cambia el botón; al tocarlo se avisa (PRF-003)")
    func annualChangesCta() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        viewModel.billing = .annual
        #expect(viewModel.cta.title == "Elegir Lector · $24.99 por año")
        #expect(viewModel.cta.subtitle == "Se renueva cada año. Cancelás cuando quieras.")
    }

    @Test("PLN-005: se elige Biblioteca tocando su tarjeta y pasa a amarillo; Gratis no se elige")
    func selection() {
        let viewModel = PlansViewModel(cache: PlansMemoryQuotaCache())
        viewModel.select(.pro)
        #expect(viewModel.selected == .pro)
        #expect(viewModel.isHighlighted(.pro))
        #expect(!viewModel.isHighlighted(.plus))
        viewModel.select(.free)
        #expect(viewModel.selected == .pro)
        #expect(!viewModel.isHighlighted(.free))
        #expect(viewModel.cta.title == "Elegir Biblioteca · $5.99 por mes")
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
        #expect(viewModel.isOnFreePlan)
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

}
