import ReaderDomain
import SwiftUI

/// Tarjeta amarilla de Mi plan con el plan siguiente (PLN-012, PLN-017).
struct MyPlanOffer: Equatable {
    let title: String
    let body: String
}

/// P04 Mi plan (PLN-012 en Gratis, PLN-017 en Lector y Biblioteca), como `MyPlanViewModel` de Android: primero la cuota
/// guardada, después la del servidor.
@MainActor @Observable
final class MyPlanViewModel {
    /// nil mientras no hay ningún valor (ni leído ni guardado).
    private(set) var usage: PlanUsage?
    private(set) var quota: StorageQuota?

    private let source: QuotaSource?
    private let cache: QuotaCache

    /// `source` es nil sin Firebase: queda lo guardado.
    init(quota source: QuotaSource?, cache: QuotaCache) {
        self.source = source
        self.cache = cache
        quota = cache.last()
        usage = PlanUsage(quota)
    }

    func load() async {
        if quota == nil, let saved = cache.last() { show(saved) }
        guard let source, let current = try? await source.current() else { return }
        cache.save(current)
        show(current)
    }

    /// Plan de la cuenta (PLN-014); sin dato, Gratis.
    var plan: PlanId { PlanCatalog.planOf(quota) }

    var planName: String { plan.name }

    /// "12 MB de 15 MB".
    var storageText: String? { usage.map { "\($0.used) de \($0.total)" } }

    /// PLN-012, PLN-017, ACC-010: "2 MB", el máximo por archivo de la cuenta (el del servidor si se conoce).
    var maxFile: String {
        let bytes = PlanCatalog.maxFileBytes(quota)
        return formatStorage(bytes, unit: StorageUnit.forTotal(bytes))
    }

    /// La oferta del plan siguiente, con sus valores por defecto (`DefaultPlans`); en Biblioteca no hay.
    var offer: MyPlanOffer? {
        switch plan {
        case .free:
            MyPlanOffer(
                title: String(localized: "Con Lector: \(PlanCatalog.storageText(.plus, quota: quota)) en la nube"),
                body: String(localized: "Archivos de hasta \(PlanCatalog.maxFileText(.plus)), temas y fuentes.")
            )
        case .plus:
            MyPlanOffer(
                title: String(localized: "Con Biblioteca: \(PlanCatalog.storageText(.pro, quota: quota)) en la nube"),
                body: String(localized: "Archivos de hasta \(PlanCatalog.maxFileText(.pro)).")
            )
        case .pro:
            nil
        }
    }

    private func show(_ quota: StorageQuota) {
        self.quota = quota
        usage = PlanUsage(quota)
    }
}

/// P04 Mi plan, dentro del tab Perfil y sin barra inferior. Diseños: "P04 Mi plan Gratis" y "P04 Mi plan · Biblioteca".
struct MyPlanView: View {
    let viewModel: MyPlanViewModel
    var onBack: () -> Void = {}
    var onChangePlan: () -> Void = {}
    var comingSoon: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                MyPlanContent(viewModel: viewModel, onBack: onBack)
            }
            .scrollBounceBehavior(.basedOnSize)
            MyPlanButtons(onChangePlan: onChangePlan, comingSoon: comingSoon)
        }
        .frame(maxWidth: WindowLayout.contentMaxWidth)
        .frame(maxWidth: .infinity)
        .background(MarginColors.paper)
        .toolbar(.hidden)
        .task { await viewModel.load() }
    }
}

/// El contenido de Mi plan sin los botones de abajo: se puede dibujar solo.
struct MyPlanContent: View {
    let viewModel: MyPlanViewModel
    var onBack: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onBack) {
                HStack(spacing: 0) {
                    ProfileIcon(name: "ArrowBack", size: 24).frame(width: 44, height: 44)
                    Text("Perfil").font(.app(14, .semibold)).foregroundStyle(MarginColors.muted)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(EdgeInsets(top: 4, leading: 10, bottom: 0, trailing: 10))
            .accessibilityLabel(Text("Volver a Perfil"))
            Text("Mi plan")
                .font(.app(40))
                .tracking(-0.045 * 40)
                .foregroundStyle(MarginColors.ink)
                .padding(EdgeInsets(top: 10, leading: 20, bottom: 0, trailing: 20))
                .accessibilityAddTraits(.isHeader)
            planCard
            if let offer = viewModel.offer { upsell(offer) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var planCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(viewModel.planName).font(.app(22, .bold)).foregroundStyle(MarginColors.ink)
                Spacer()
                YourPlanChip(check: false)
            }
            .accessibilityElement(children: .combine)
            VStack(spacing: 8) {
                HStack {
                    Text("Espacio en la nube").font(.app(14, .semibold)).foregroundStyle(MarginColors.ink)
                    Spacer(minLength: 8)
                    Text(viewModel.storageText ?? "—").font(.mono(13)).foregroundStyle(MarginColors.ink)
                }
                PlanUsageBar(fraction: viewModel.usage?.fraction ?? 0)
            }
            .accessibilityElement(children: .combine)
            detail("Dispositivos", "Todos, sin límite")
            Text("Archivos de hasta \(viewModel.maxFile)")
                .font(.app(14, .medium))
                .foregroundStyle(MarginColors.ink2)
        }
        .padding(18)
        .background(MarginColors.field, in: RoundedRectangle(cornerRadius: 24))
        .padding(EdgeInsets(top: 18, leading: 20, bottom: 0, trailing: 20))
    }

    private func detail(_ label: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer(minLength: 8)
            Text(value)
        }
        .font(.app(14, .medium))
        .foregroundStyle(MarginColors.ink2)
        .accessibilityElement(children: .combine)
    }

    private func upsell(_ offer: MyPlanOffer) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(offer.title).font(.app(15, .bold)).foregroundStyle(MarginColors.onYellow)
            Text(offer.body)
                .font(.app(13, .medium))
                .foregroundStyle(MarginColors.onYellowMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(MarginColors.yellow, in: RoundedRectangle(cornerRadius: 20))
        .padding(EdgeInsets(top: 12, leading: 20, bottom: 20, trailing: 20))
        .accessibilityElement(children: .combine)
    }
}

/// "Cambiar de plan" (abre P01) y "Restaurar compras" (avisa, PRF-003).
struct MyPlanButtons: View {
    var onChangePlan: () -> Void = {}
    var comingSoon: () -> Void = {}

    var body: some View {
        VStack(spacing: 6) {
            PlanPrimaryButton(title: String(localized: "Cambiar de plan"), arrow: true, action: onChangePlan)
            Button(action: comingSoon) {
                Text("Restaurar compras")
                    .font(.app(14, .semibold))
                    .underline()
                    .foregroundStyle(MarginColors.ink)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(EdgeInsets(top: 8, leading: 20, bottom: 8, trailing: 20))
    }
}
