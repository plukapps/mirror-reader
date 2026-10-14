import ReaderDomain
import SwiftUI

/// P04 Mi plan (PLN-012), como `MyPlanViewModel` de Android: primero la cuota guardada, después la del servidor.
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

    /// En esta rebanada solo existe la variante Gratis.
    var planName: String { PlanId.free.name }

    /// "12 MB de 15 MB".
    var storageText: String? { usage.map { "\($0.used) de \($0.total)" } }

    var readerStorage: String { PlanCatalog.storageText(.plus, quota: quota) }

    /// PLN-012: "2 MB", el tamaño máximo por archivo de Gratis.
    var maxFile: String { PlanCatalog.maxFileText(.free) }

    private func show(_ quota: StorageQuota) {
        self.quota = quota
        usage = PlanUsage(quota)
    }
}

/// P04 Mi plan (Gratis), dentro del tab Perfil y sin barra inferior. Diseño: "P04 Mi plan Gratis".
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
            upsell
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

    private var upsell: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Con Lector: \(viewModel.readerStorage) en la nube").font(.app(15, .bold)).foregroundStyle(MarginColors.onYellow)
            Text("Archivos de hasta \(PlanCatalog.maxFileText(.plus)), temas y fuentes.")
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
