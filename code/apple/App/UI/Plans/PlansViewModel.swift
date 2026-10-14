import Foundation
import Observation
import ReaderDomain

/// P01 Planes (PLN-002 a PLN-006), como `PlansViewModel` de Android. Lee la cuota guardada, sin red.
@MainActor @Observable
final class PlansViewModel {
    /// PLN-004: arranca en mensual.
    var billing: Billing = .monthly
    /// PLN-005: arranca elegido Lector. Gratis no se elige.
    private(set) var selected: PlanId = .plus
    /// PLN-006: la tabla comparativa, desplegada o no.
    private(set) var tableOpen = false
    private(set) var quota: StorageQuota?

    private let cache: QuotaCache

    init(cache: QuotaCache) {
        self.cache = cache
        quota = cache.last()
    }

    /// Vuelve a leer la cuota guardada (puede cambiar con una pasada de sincronización).
    func load() {
        quota = cache.last()
    }

    /// PLN-003: nil si nunca se leyó la cuota; entonces se oculta la fila.
    var usage: PlanUsage? { PlanUsage(quota) }

    /// PLN-005: "Tu plan" va en Gratis si es el plan de la cuenta. Sin dato se asume Gratis (no hay otro todavía).
    var isOnFreePlan: Bool { quota?.isFreePlan ?? true }

    func select(_ plan: PlanId) {
        guard plan != .free else { return }
        selected = plan
    }

    /// PLN-005: la tarjeta del plan elegido va en amarillo. Gratis no se elige.
    func isHighlighted(_ plan: PlanId) -> Bool { plan != .free && plan == selected }

    func toggleTable() {
        tableOpen.toggle()
    }

    var cta: PlanCta { PlanCta(selected, billing) }

    var comparison: [PlanComparisonRow] { PlanComparisonRow.rows(quota: quota) }

    /// "15 MB", "5 GB": el espacio de cada tarjeta.
    func storage(_ plan: PlanId) -> String { PlanCatalog.storageText(plan, quota: quota) }
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

    /// Un ViewModel nuevo cada vez que se abre: vuelve a arrancar en anual con Lector elegido.
    func makePlans() -> PlansViewModel {
        PlansViewModel(cache: cache)
    }
}

/// Funciones pagas que se muestran en la hoja de P03 (PLN-015). Exportar notas (P03a) llega con las anotaciones.
enum PaidFeature: Equatable {
    case themesAndFonts
}
