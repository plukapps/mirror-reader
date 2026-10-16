import ReaderDomain
import SwiftUI

/// HOM-012 (diseños "02 — Home" y H0): el logo y "tuck." arriba; debajo, el saludo y el avatar, que abre Perfil.
/// Con sesión, la inicial; sin cuenta, el ícono de una persona.
struct HomeHeader: View {
    let greeting: String
    let avatarInitial: String?
    let onOpenProfile: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image("Logo").renderingMode(.template).resizable().foregroundStyle(MarginColors.ink).frame(width: 20, height: 21.5)
                (Text("tuck") + Text(".").foregroundStyle(MarginColors.readingDot))
                    .font(.app(18, .medium))
                    .tracking(-0.03 * 18)
                    .foregroundStyle(MarginColors.ink)
            }
            .accessibilityHidden(true)
            .padding(.top, 12)
            HStack(alignment: .top) {
                Text(greeting)
                    .font(.app(34))
                    .tracking(-0.04 * 34)
                    .foregroundStyle(MarginColors.ink)
                Spacer()
                Button(action: onOpenProfile) {
                    Group {
                        if let avatarInitial {
                            Text(avatarInitial).font(.app(16, .bold))
                        } else {
                            Image(systemName: "person.fill").font(.system(size: 18))
                        }
                    }
                    .foregroundStyle(MarginColors.onYellow)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(MarginColors.yellow))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Abrir perfil")
            }
            .padding(.top, 16)
        }
        .padding(.horizontal, WindowLayout.horizontalPadding)
    }
}

/// Colores fijos del banner, como la cabecera amarilla de Perfil: se ve igual en modo claro y oscuro.
private enum BannerColors {
    static let background = CoverColors.bone
    static let ink = CoverColors.ink
    static let soft = Color(hex: 0x3A3830)
    static let muted = Color(hex: 0x6B6650)
}

/// GST-023 (diseños H0b "6e" y H0 "6b"): banner sin cuenta de Inicio. Toda la tarjeta abre el alta corta; la cruz lo
/// esconde por unos días (GST-010, GST-026). La opción la elige GST-025.
struct GuestBanner: View {
    let variant: GuestBannerVariant
    let onCreateAccount: () -> Void
    let onDismiss: () -> Void

    private var title: String {
        switch variant {
        case .allDevices: "Tus libros, en todos tus dispositivos"
        case .noBackup: "Tus libros sin respaldo"
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button(action: onCreateAccount) {
                HStack(spacing: 16) {
                    BannerCovers(badge: variant == .allDevices ? "arrow.triangle.2.circlepath" : "icloud.and.arrow.up", border: BannerColors.background)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(title)
                            .font(.app(17, .bold))
                            .tracking(-0.025 * 17)
                            .foregroundStyle(BannerColors.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        switch variant {
                        case .allDevices:
                            HStack(spacing: 6) {
                                ForEach(["iphone", "ipad", "laptopcomputer"], id: \.self) {
                                    Image(systemName: $0).font(.system(size: 17)).foregroundStyle(BannerColors.soft)
                                }
                                Text("gratis").font(.app(12, .medium)).foregroundStyle(BannerColors.muted).padding(.leading, 2)
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel("En el teléfono, la tablet y la computadora, gratis")
                        case .noBackup:
                            Text("Si cambiás de celu, se pierden. Guardalos gratis.")
                                .font(.app(12, .medium))
                                .foregroundStyle(BannerColors.soft)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        HStack(spacing: 4) {
                            Text("Crear cuenta").font(.app(13, .bold))
                            Image(systemName: "arrow.right").font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundStyle(BannerColors.ink)
                    }
                    .padding(.trailing, 34)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(16)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            Button(action: onDismiss) {
                PlanIcon(name: "Close", size: 20, color: BannerColors.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(4)
            .accessibilityLabel("Cerrar aviso")
        }
        .background(BannerColors.background, in: RoundedRectangle(cornerRadius: 24))
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 16)
    }
}

/// GST-010 (L1): "Tus libros están solo en este dispositivo." con "Crear cuenta" y la cruz, con el aspecto del aviso
/// del 80 % (PLN-011).
struct GuestNoticeCard: View {
    let onCreateAccount: () -> Void
    let onClose: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Tus libros están solo en este dispositivo.").font(.app(14, .semibold)).foregroundStyle(MarginColors.ink)
                Button(action: onCreateAccount) {
                    (Text("Creá una cuenta para tenerlos en todos. ").foregroundStyle(MarginColors.ink2)
                        + Text("Crear cuenta").fontWeight(.bold).underline().foregroundStyle(MarginColors.ink))
                        .font(.app(13, .medium))
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Creá una cuenta para tenerlos en todos. Crear cuenta"))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onClose) {
                PlanIcon(name: "Close", size: 22, color: MarginColors.muted)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.top, -10)
            .accessibilityLabel(Text("Cerrar aviso"))
        }
        .padding(EdgeInsets(top: 14, leading: 16, bottom: 12, trailing: 6))
        .background(MarginColors.field, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(MarginColors.off, lineWidth: 1.5))
    }
}
