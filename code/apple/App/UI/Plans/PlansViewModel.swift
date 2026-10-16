import Foundation
import Observation
import ReaderDomain

/// P01 Planes (PLN-002 a PLN-006), como `PlansViewModel` de Android. Lee la cuota guardada, sin red.
@MainActor @Observable
final class PlansViewModel {
    /// PLN-004: arranca en mensual.
    var billing: Billing = .monthly
    /// PLN-005: arranca elegido Plus. Free no se elige.
    private(set) var selected: PlanId = .plus
    /// PLN-006: la tabla comparativa, desplegada o no.
    private(set) var tableOpen = false
    private(set) var quota: StorageQuota?
    /// ACC-007, K-290: el catálogo de planes vigente; cambia si llega otro de Remote Config.
    private(set) var plans: [Plan]

    private let cache: QuotaCache
    @ObservationIgnored private let catalog: PlanCatalogStore

    init(cache: QuotaCache, catalog: PlanCatalogStore = .shared) {
        self.cache = cache
        self.catalog = catalog
        quota = cache.last()
        plans = catalog.plans
    }

    /// Sigue los cambios del catálogo mientras la pantalla está a la vista (`.task`).
    func followCatalog() async {
        for await plans in catalog.changes() { self.plans = plans }
    }

    /// Vuelve a leer la cuota guardada (puede cambiar con una pasada de sincronización).
    func load() {
        quota = cache.last()
    }

    /// PLN-003: nil si nunca se leyó la cuota; entonces se oculta la fila.
    var usage: PlanUsage? { PlanUsage(quota) }

    /// PLN-005, PLN-017: el plan de la cuenta, que lleva "Tu plan". Sin dato, Free.
    var accountPlan: PlanId { PlanCatalog.planOf(quota) }

    func select(_ plan: PlanId) {
        guard plan != .free else { return }
        selected = plan
    }

    /// PLN-005: la tarjeta del plan elegido va en amarillo. Free no se elige.
    func isHighlighted(_ plan: PlanId) -> Bool { plan != .free && plan == selected }

    func toggleTable() {
        tableOpen.toggle()
    }

    var cta: PlanCta { PlanCta(selected, billing, catalog: plans) }

    var comparison: [PlanComparisonRow] { PlanComparisonRow.rows(quota: quota, catalog: plans) }

    /// "15 MB", "5 GB": el espacio de cada tarjeta.
    func storage(_ plan: PlanId) -> String { PlanCatalog.storageText(plan, quota: quota, in: plans) }
}

/// Quién abre Planes (PLN-002): Perfil, Mi plan, la Biblioteca y las hojas de P02 y P03. Se presenta a pantalla
/// completa desde la raíz.
@MainActor @Observable
final class PlansPresenter {
    /// P01 a la vista.
    var plansOpen = false

    private let cache: QuotaCache

    init(cache: QuotaCache) {
        self.cache = cache
    }

    func openPlans() {
        plansOpen = true
    }

    func closePlans() {
        plansOpen = false
    }

    /// "Ver planes" desde una hoja: Planes se abre cuando la hoja termina de cerrarse (dos presentaciones no se pisan).
    private(set) var plansAfterSheet = false

    func openPlansAfterSheet() {
        plansAfterSheet = true
    }

    /// La hoja se cerró: si se tocó "Ver planes", se abre Planes.
    func sheetDismissed() {
        guard plansAfterSheet else { return }
        plansAfterSheet = false
        plansOpen = true
    }

    /// Un ViewModel nuevo cada vez que se abre: vuelve a arrancar en anual con Plus elegido.
    func makePlans() -> PlansViewModel {
        PlansViewModel(cache: cache)
    }
}

/// Funciones pagas que se muestran en la hoja de P03 (PLN-015). Exportar notas (P03a) llega con las anotaciones.
enum PaidFeature: Equatable {
    case themesAndFonts
}
