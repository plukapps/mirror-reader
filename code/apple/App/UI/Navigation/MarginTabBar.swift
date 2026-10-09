import SwiftUI

/// Barra inferior del diseño (pantallas 02, 03 y 05), igual que `MarginBottomBar` de Android: píldora
/// tinta flotante, solo íconos, y el destino activo en una píldora amarilla. Sin fondo alrededor ni
/// sombra: el contenido pasa por debajo. El nombre de cada destino queda para VoiceOver (HOM-007).
struct MarginTabBar: View {
    let selected: MainDestination
    let onSelect: (MainDestination) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(MainDestination.allCases) { destination in
                Item(destination: destination, selected: destination == selected) { onSelect(destination) }
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
        .frame(height: 60)
        .background(MarginColors.ink, in: Capsule())
        .padding(.horizontal, 40)
        .padding(.bottom, 12)
    }
}

private struct Item: View {
    let destination: MainDestination
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(selected ? destination.selectedIcon : destination.icon)
                .resizable()
                .frame(width: 24, height: 24)
                .foregroundStyle(selected ? MarginColors.ink : MarginColors.inkMuted)
                .frame(width: 52, height: 44)
                .background(selected ? MarginColors.yellow : .clear, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(destination.label))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// Aviso breve de HOM-006 ("Llega más adelante."). iOS no tiene toast: una píldora tinta sobre la barra.
struct ComingSoonNotice: View {
    var body: some View {
        Text("Llega más adelante.")
            .font(.app(14, .semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(MarginColors.ink, in: Capsule())
    }
}

#Preview {
    VStack {
        Spacer()
        ComingSoonNotice()
        MarginTabBar(selected: .home) { _ in }
    }
    .background(MarginColors.paper)
}
