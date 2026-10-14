import ReaderDomain
import SwiftUI

/// Lo que se puede tocar en Planes. Comprar y lo legal todavía no existen: avisan (PRF-003).
struct PlansActions {
    var close: () -> Void = {}
    var comingSoon: () -> Void = {}
}

/// P01 Planes (PLN-002 a PLN-006, PLN-013), a pantalla completa y sin barra inferior. Diseño: "1a" (teléfono) y
/// "P01 Planes tablet" (ancho de 600 pt o más, tres columnas).
struct PlansView: View {
    let viewModel: PlansViewModel
    var actions = PlansActions()
    /// El aviso "Llega más adelante." se muestra aquí: la barra inferior de la app queda tapada.
    var showsComingSoon = false

    var body: some View {
        GeometryReader { proxy in
            let wide = proxy.size.width >= PlansLayout.tabletMinWidth
            ScrollView {
                PlansContent(viewModel: viewModel, actions: actions, wide: wide)
                    .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                PurchaseBar(viewModel: viewModel, actions: actions, wide: wide)
                    .overlay(alignment: .top) {
                        if showsComingSoon {
                            ComingSoonNotice()
                                .fixedSize()
                                .alignmentGuide(.top) { $0[.bottom] + 12 }
                                .transition(.opacity)
                        }
                    }
                    .animation(.easeOut(duration: 0.2), value: showsComingSoon)
            }
        }
        .background(MarginColors.paper.ignoresSafeArea())
        .onAppear { viewModel.load() }
    }
}

/// P01 con su propio ViewModel (arranca de cero cada vez que se abre) y el aviso de lo que todavía no existe. Se
/// presenta desde la raíz y desde el lector (PLN-015).
struct PlansScreen: View {
    let presenter: PlansPresenter
    let tabs: TabBarModel
    let onClose: () -> Void
    @State private var viewModel: PlansViewModel?

    var body: some View {
        Group {
            if let viewModel {
                PlansView(
                    viewModel: viewModel,
                    actions: PlansActions(close: onClose, comingSoon: tabs.showComingSoon),
                    showsComingSoon: tabs.showsComingSoon
                )
            } else {
                MarginColors.paper.ignoresSafeArea()
            }
        }
        .onAppear { if viewModel == nil { viewModel = presenter.makePlans() } }
    }
}

enum PlansLayout {
    /// PLN-005: tres columnas desde 600 pt de ancho.
    static let tabletMinWidth: CGFloat = 600
}

/// El contenido desplazable de P01, sin la barra de compra: se puede dibujar entero (para compararlo con el diseño).
struct PlansContent: View {
    let viewModel: PlansViewModel
    var actions = PlansActions()
    var wide = false

    var body: some View {
        VStack(spacing: 0) {
            if wide { tabletHeader } else { phoneHeader }
            cards
            tableToggle
            if viewModel.tableOpen {
                ComparisonTable(rows: viewModel.comparison, wide: wide)
                    .padding(EdgeInsets(top: wide ? 0 : 12, leading: wide ? 28 : 20, bottom: wide ? 20 : 0, trailing: wide ? 28 : 20))
            }
            if !wide {
                Guarantee(iconSize: 22)
                    .padding(EdgeInsets(top: 20, leading: 20, bottom: 0, trailing: 20))
                LegalLinks(height: 44, action: actions.comingSoon)
                    .frame(maxWidth: .infinity)
                    .padding(EdgeInsets(top: 14, leading: 12, bottom: 16, trailing: 12))
            }
        }
        .background(MarginColors.paper)
    }

    // MARK: Cabecera (PLN-003, PLN-004)

    private var phoneHeader: some View {
        VStack(alignment: .leading, spacing: 0) {
            CloseButton(circle: false, action: actions.close)
                .padding(EdgeInsets(top: 4, leading: 10, bottom: 0, trailing: 10))
            if let usage = viewModel.usage {
                UsageRow(usage: usage)
                    .padding(EdgeInsets(top: 6, leading: 20, bottom: 0, trailing: 20))
            }
            Text("Tu biblioteca entera, en todos lados.")
                .font(.app(38))
                .tracking(-0.04 * 38)
                .lineSpacing(38 * 0.02 - 8)
                .foregroundStyle(MarginColors.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .padding(EdgeInsets(top: 26, leading: 20, bottom: 0, trailing: 20))
            BillingToggle(viewModel: viewModel, height: 52)
                .padding(EdgeInsets(top: 22, leading: 20, bottom: 0, trailing: 20))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var tabletHeader: some View {
        VStack(spacing: 0) {
            HStack(alignment: .center) {
                CloseButton(circle: true, action: actions.close)
                Spacer()
                if let usage = viewModel.usage {
                    UsageRow(usage: usage, spacing: 6).frame(width: 240)
                }
            }
            .padding(EdgeInsets(top: 20, leading: 28, bottom: 0, trailing: 28))
            VStack(spacing: 20) {
                Text("Tu biblioteca entera, en todos lados.")
                    .font(.app(52))
                    .tracking(-0.045 * 52)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(MarginColors.ink)
                    .accessibilityAddTraits(.isHeader)
                BillingToggle(viewModel: viewModel, height: 48).frame(width: 340)
            }
            .padding(.top, 8)
            .padding(.horizontal, 28)
        }
    }

    // MARK: Tarjetas (PLN-005)

    @ViewBuilder
    private var cards: some View {
        if wide {
            HStack(alignment: .top, spacing: 16) {
                ForEach(PlanCatalog.all, id: \.id) { plan in
                    PlanCard(plan: plan, viewModel: viewModel, wide: true).frame(maxHeight: .infinity, alignment: .top)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(EdgeInsets(top: 24, leading: 28, bottom: 0, trailing: 28))
        } else {
            VStack(spacing: 12) {
                ForEach(PlanCatalog.all, id: \.id) { plan in
                    PlanCard(plan: plan, viewModel: viewModel, wide: false)
                }
            }
            .padding(EdgeInsets(top: 16, leading: 20, bottom: 0, trailing: 20))
        }
    }

    // MARK: Tabla (PLN-006)

    @ViewBuilder
    private var tableToggle: some View {
        let icon = viewModel.tableOpen ? "ExpandLess" : "ExpandMore"
        if wide {
            Button(action: viewModel.toggleTable) {
                HStack(spacing: 4) {
                    Text("Ver todo lo que incluye").font(.app(15, .semibold)).underline()
                    PlanIcon(name: icon, size: 22)
                }
                .foregroundStyle(MarginColors.ink)
                .padding(.horizontal, 18)
                .frame(height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(EdgeInsets(top: 14, leading: 0, bottom: 6, trailing: 0))
            .accessibilityValue(Text(viewModel.tableOpen ? "Desplegado" : "Plegado"))
        } else {
            Button(action: viewModel.toggleTable) {
                HStack(spacing: 6) {
                    Text("Ver todo lo que incluye").font(.app(15, .semibold))
                    PlanIcon(name: icon, size: 22)
                }
                .foregroundStyle(MarginColors.ink)
                .frame(maxWidth: .infinity, minHeight: 52)
                .overlay(Capsule().strokeBorder(MarginColors.ink, lineWidth: 1.5))
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .padding(EdgeInsets(top: 16, leading: 20, bottom: 0, trailing: 20))
            .accessibilityValue(Text(viewModel.tableOpen ? "Desplegado" : "Plegado"))
        }
    }
}

// MARK: - Piezas de P01

private struct CloseButton: View {
    let circle: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            PlanIcon(name: "Close", size: circle ? 24 : 26)
                .frame(width: 44, height: 44)
                .background(circle ? MarginColors.track : .clear, in: Circle())
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Cerrar"))
    }
}

/// PLN-003: "Usás X de Y", la proporción en monoespaciada y la barra.
private struct UsageRow: View {
    let usage: PlanUsage
    var spacing: CGFloat = 8

    var body: some View {
        VStack(spacing: spacing) {
            HStack(alignment: .firstTextBaseline) {
                Text(usage.text).font(.app(14, .semibold)).foregroundStyle(MarginColors.ink)
                Spacer(minLength: 8)
                Text(usage.ratio).font(.mono(12)).foregroundStyle(MarginColors.muted)
            }
            PlanUsageBar(fraction: usage.fraction)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(usage.text))
        .accessibilityValue(Text(usage.ratio))
    }
}

/// PLN-004: Mensual / Anual, con "Ahorrás 30 %" en Anual.
private struct BillingToggle: View {
    @Bindable var viewModel: PlansViewModel
    let height: CGFloat

    var body: some View {
        HStack(spacing: 0) {
            segment(.monthly, "Mensual")
            segment(.annual, "Anual", badge: "Ahorrás \(PlanCatalog.annualSavingPercent) %")
        }
        .padding(4)
        .frame(height: height)
        .background(MarginColors.track, in: Capsule())
    }

    private func segment(_ billing: Billing, _ label: String, badge: String? = nil) -> some View {
        let selected = viewModel.billing == billing
        return Button { viewModel.billing = billing } label: {
            HStack(spacing: 6) {
                Text(label).font(.app(14, selected ? .bold : .semibold))
                if let badge {
                    Text(badge)
                        .font(.app(11, .bold))
                        .foregroundStyle(MarginColors.onYellow)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(MarginColors.yellow, in: Capsule())
                }
            }
            // En oscuro se invierte (fondo claro, texto oscuro): la tinta sobre la pista no se distinguiría.
            .foregroundStyle(selected ? MarginColors.field : MarginColors.ink)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(selected ? MarginColors.ink : .clear, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

/// Una tarjeta de plan (PLN-005). Lector va en amarillo; Lector y Biblioteca se eligen tocándolas.
private struct PlanCard: View {
    let plan: Plan
    let viewModel: PlansViewModel
    let wide: Bool

    private var isReader: Bool { plan.id == .plus }
    /// PLN-005: la tarjeta elegida va en amarillo con borde tinta; las demás, con el fondo y el borde de siempre.
    private var highlighted: Bool { viewModel.isHighlighted(plan.id) }
    private var isFree: Bool { plan.id == .free }
    private var selected: Bool { viewModel.selected == plan.id }
    private var text: Color { highlighted ? MarginColors.onYellow : MarginColors.ink }
    private var secondary: Color { highlighted ? MarginColors.onYellowMuted : MarginColors.ink2 }
    private var radius: CGFloat { wide ? 28 : 24 }

    var body: some View {
        if isFree {
            card
        } else {
            Button { viewModel.select(plan.id) } label: { card.contentShape(RoundedRectangle(cornerRadius: radius)) }
                .buttonStyle(.plain)
        }
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: wide ? 14 : 12) {
            header
            Text(plan.tagline)
                .font(.app(wide ? 15 : 14))
                .foregroundStyle(secondary)
                .padding(.top, wide ? -10 : -8)
            storage
            VStack(alignment: .leading, spacing: 0) {
                ForEach(plan.includes, id: \.self) { item in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        PlanIcon(name: "Check", size: wide ? 19 : 18, color: text).alignmentGuide(.firstTextBaseline) { $0.height * 0.78 }
                        Text(item)
                            .font(.app(wide ? 15 : 14, .medium))
                            .lineSpacing((wide ? 15 : 14) * 0.1)
                            .foregroundStyle(text)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            if wide { Spacer(minLength: 0) }
            price
        }
        .padding(wide ? 24 : 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(highlighted ? MarginColors.yellow : MarginColors.field, in: RoundedRectangle(cornerRadius: radius))
        .overlay(
            RoundedRectangle(cornerRadius: radius)
                .strokeBorder(highlighted ? MarginColors.onYellow : MarginColors.off, lineWidth: highlighted ? 2 : 1.5)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(accessibilityText))
        .accessibilityAddTraits(isFree ? [] : (selected ? [.isButton, .isSelected] : [.isButton]))
    }

    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                Text(plan.name).font(.app(wide ? 24 : 20, .bold)).tracking(-0.02 * (wide ? 24 : 20)).foregroundStyle(text)
                if isReader {
                    Text("Más elegido")
                        .font(.app(wide ? 12 : 11, .bold))
                        .foregroundStyle(MarginColors.yellow)
                        .padding(.horizontal, wide ? 9 : 8)
                        .padding(.vertical, 4)
                        .background(MarginColors.onYellow, in: Capsule())
                }
            }
            Spacer(minLength: 8)
            if isFree {
                if viewModel.isOnFreePlan {
                    YourPlanChip(height: wide ? 30 : 28, fontSize: wide ? 13 : 12)
                }
            } else {
                PlanIcon(name: selected ? "RadioChecked" : "RadioUnchecked", size: wide ? 28 : 26, color: text)
            }
        }
    }

    private var storage: some View {
        let parts = viewModel.storage(plan.id).split(separator: " ", maxSplits: 1).map(String.init)
        let size: CGFloat = wide ? 64 : 48
        return HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(parts.first ?? "").font(.app(size, .light)).tracking(-(wide ? 0.045 : 0.04) * size)
            Text("\(parts.count > 1 ? parts[1] : "") en la nube").font(.app(wide ? 15 : 14, .semibold))
        }
        .foregroundStyle(text)
    }

    /// PLN-005: las tres tarjetas con la misma estructura: separador, precio grande con el período y una línea chica.
    private var price: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(plan.price(viewModel.billing)).font(.app(wide ? 28 : 26, .bold)).tracking(-0.02 * 26)
                Text(viewModel.billing.perLabel).font(.app(wide ? 15 : 14, .semibold))
            }
            .foregroundStyle(text)
            Text(plan.periodNote(viewModel.billing))
                .font(.app(wide ? 13 : 12, .medium))
                .foregroundStyle(highlighted ? MarginColors.onYellowMuted : MarginColors.muted)
        }
        .padding(.top, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .top) { (highlighted ? MarginColors.onYellowLine : MarginColors.line).frame(height: 1) }
    }

    private var accessibilityText: String {
        let storage = "\(viewModel.storage(plan.id)) en la nube"
        let price = "\(plan.price(viewModel.billing)) \(viewModel.billing.perLabel), \(plan.periodNote(viewModel.billing))"
        var parts = [plan.name, plan.tagline, storage, price]
        if isReader { parts.insert("Más elegido", at: 1) }
        if isFree, viewModel.isOnFreePlan { parts.append("Tu plan") }
        return parts.joined(separator: ", ")
    }
}

/// PLN-006: la tabla comparativa. Cada fila se lee entera (PLN-013).
private struct ComparisonTable: View {
    let rows: [PlanComparisonRow]
    let wide: Bool

    private var columns: [CGFloat] { wide ? [160, 160, 160] : [50, 50, 56] }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Spacer(minLength: 0)
                header("Gratis", width: columns[0], color: MarginColors.muted)
                header("Lector", width: columns[1], color: MarginColors.ink)
                header(wide ? "Biblioteca" : "Biblio.", width: columns[2], color: MarginColors.muted)
            }
            .frame(height: wide ? 44 : 40)
            .accessibilityHidden(true)
            ForEach(rows, id: \.feature) { row in
                VStack(spacing: 0) {
                    MarginColors.track.frame(height: 1)
                    HStack(spacing: 0) {
                        Text(row.feature)
                            .foregroundStyle(MarginColors.ink)
                            .lineSpacing(13 * 0.3 - 2)
                            .padding(.vertical, 6)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .fixedSize(horizontal: false, vertical: true)
                        cell(row.free, width: columns[0])
                        cell(row.plus, width: columns[1]).fontWeight(.bold).foregroundStyle(MarginColors.ink)
                        cell(row.pro, width: columns[2])
                    }
                    .font(.app(wide ? 14 : 13, .medium))
                    .frame(minHeight: 44)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(row.feature))
                .accessibilityValue(Text("Gratis: \(spoken(row.free)). Lector: \(spoken(row.plus)). Biblioteca: \(spoken(row.pro))."))
            }
        }
        .padding(EdgeInsets(top: wide ? 8 : 6, leading: wide ? 24 : 14, bottom: wide ? 12 : 8, trailing: wide ? 24 : 14))
        .background(MarginColors.field, in: RoundedRectangle(cornerRadius: wide ? 24 : 20))
    }

    private func header(_ text: String, width: CGFloat, color: Color) -> some View {
        Text(text)
            .textCase(.uppercase)
            .font(.app(wide ? 12 : 11, .bold))
            .tracking((wide ? 0.06 : 0.04) * (wide ? 12 : 11))
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(width: width)
    }

    private func cell(_ text: String, width: CGFloat) -> some View {
        Text(text)
            .foregroundStyle(MarginColors.ink2)
            .multilineTextAlignment(.center)
            .frame(width: width)
    }

    private func spoken(_ value: String) -> String {
        switch value {
        case "✓": "sí"
        case "—": "no"
        default: value
        }
    }
}

/// "Si cancelás, no perdés nada…" en Newsreader itálica.
private struct Guarantee: View {
    let iconSize: CGFloat

    var body: some View {
        HStack(alignment: .top, spacing: iconSize == 22 ? 10 : 8) {
            PlanIcon(name: "VerifiedUser", size: iconSize)
            Text("Si cancelás, no perdés nada: tus libros y notas quedan.")
                .font(.serifItalic(17))
                .lineSpacing(17 * 0.35 - 4)
                .foregroundStyle(MarginColors.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: iconSize == 22 ? .infinity : nil, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// "Restaurar compras · Términos · Privacidad": todos avisan (PRF-003).
private struct LegalLinks: View {
    let height: CGFloat
    let action: () -> Void

    var body: some View {
        HStack(spacing: height == 44 ? 4 : 2) {
            link("Restaurar compras")
            Text("·").accessibilityHidden(true)
            link("Términos")
            Text("·").accessibilityHidden(true)
            link("Privacidad")
        }
        .font(.app(13, .medium))
        .foregroundStyle(MarginColors.ink2)
    }

    private func link(_ title: LocalizedStringKey) -> some View {
        Button(action: action) {
            Text(title).underline().padding(.horizontal, 6).frame(height: height).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Barra de compra fija al pie (PLN-006). El botón avisa (PRF-003).
struct PurchaseBar: View {
    let viewModel: PlansViewModel
    var actions = PlansActions()
    var wide = false

    var body: some View {
        let cta = viewModel.cta
        Group {
            if wide {
                HStack(spacing: 24) {
                    VStack(alignment: .leading, spacing: 4) {
                        Guarantee(iconSize: 20)
                        LegalLinks(height: 32, action: actions.comingSoon).padding(.leading, -6)
                    }
                    Spacer(minLength: 0)
                    HStack(spacing: 16) {
                        Text(cta.subtitle)
                            .font(.app(14, .medium))
                            .lineSpacing(14 * 0.35 - 3)
                            .foregroundStyle(MarginColors.ink2)
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: 260, alignment: .trailing)
                        PlanPrimaryButton(title: cta.title, action: actions.comingSoon)
                            .fixedSize()
                    }
                }
                .padding(.horizontal, 28)
                .padding(.vertical, 14)
            } else {
                VStack(spacing: 8) {
                    PlanPrimaryButton(title: cta.title, action: actions.comingSoon)
                    Text(cta.subtitle)
                        .font(.app(13, .medium))
                        .lineSpacing(13 * 0.35 - 3)
                        .foregroundStyle(MarginColors.ink2)
                        .multilineTextAlignment(.center)
                }
                .padding(EdgeInsets(top: 12, leading: 20, bottom: 8, trailing: 20))
            }
        }
        .frame(maxWidth: .infinity)
        .background(MarginColors.paper.ignoresSafeArea(edges: .bottom))
        .overlay(alignment: .top) { MarginColors.off.frame(height: 1) }
    }
}
