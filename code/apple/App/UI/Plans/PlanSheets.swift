import ReaderDomain
import SwiftUI

/// Hoja inferior de Planes (P02, P03): fondo `field`, radio 28, manija y alto según el contenido.
struct MeasuredSheet<Content: View>: View {
    @State private var height: CGFloat = 520
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            content
                .padding(EdgeInsets(top: 30, leading: 20, bottom: 8, trailing: 20))
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { if $0 > 0 { height = $0 } }
        }
        .scrollBounceBehavior(.basedOnSize)
        .presentationDetents([.height(height)])
        .presentationCornerRadius(28)
        .presentationBackground(MarginColors.field)
        .presentationDragIndicator(.visible)
    }
}

/// Título grande de hoja: 32, interlineado 1,04, espaciado -0,04 em.
struct SheetTitle: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.app(32))
            .tracking(-0.04 * 32)
            .foregroundStyle(MarginColors.ink)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Ícono en cuadro amarillo de 52, radio 16.
struct SheetIconTile: View {
    let icon: String
    var background: Color = MarginColors.yellow

    var body: some View {
        PlanIcon(name: icon, size: 28, color: MarginColors.onYellow)
            .frame(width: 52, height: 52)
            .background(background, in: RoundedRectangle(cornerRadius: 16))
    }
}

/// "Ver planes" con flecha y "Ahora no" (PLN-007 a PLN-010).
struct SheetButtons: View {
    let onSeePlans: () -> Void
    let onNotNow: () -> Void

    var body: some View {
        VStack(spacing: 6) {
            PlanPrimaryButton(title: "Ver planes", arrow: true, action: onSeePlans)
            Button(action: onNotNow) {
                Text("Ahora no")
                    .font(.app(15, .semibold))
                    .foregroundStyle(MarginColors.ink)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 20)
    }
}

/// P02a y P02b: el libro importado no entra en la nube (PLN-007, PLN-008). El libro ya está en el dispositivo.
struct QuotaBlockSheet: View {
    let notice: QuotaBlockNotice
    let onSeePlans: () -> Void
    let onNotNow: () -> Void

    var body: some View {
        MeasuredSheet { QuotaBlockContent(notice: notice, onSeePlans: onSeePlans, onNotNow: onNotNow) }
    }
}

/// El contenido de P02, sin la hoja: se puede dibujar solo (para compararlo con el diseño).
struct QuotaBlockContent: View {
    let notice: QuotaBlockNotice
    var onSeePlans: () -> Void = {}
    var onNotNow: () -> Void = {}

    private var readerStorage: String { PlanCatalog.storageText(.reader, quota: notice.quota) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            switch notice.block {
            case .full: full
            case let .doesNotFit(bookBytes, freeBytes): doesNotFit(bookBytes: bookBytes, freeBytes: freeBytes)
            }
            SheetButtons(onSeePlans: onSeePlans, onNotNow: onNotNow)
        }
    }

    // MARK: P02a

    private var full: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetIconTile(icon: "CloudOff")
            SheetTitle(text: String(localized: "Llegaste a tu espacio")).padding(.top, 16)
            (Text(notice.title).fontWeight(.bold).foregroundStyle(MarginColors.ink)
                + Text(" no se subió a la nube, pero podés leerlo en este dispositivo. Tus otros libros siguen intactos."))
                .sheetBody()
            comparison
        }
    }

    private var comparison: some View {
        let free = PlanCatalog.storageBytes(.free, quota: notice.quota)
        let reader = PlanCatalog.storageBytes(.reader, quota: notice.quota)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Text("Gratis").font(.app(13, .medium)).foregroundStyle(MarginColors.muted).frame(width: 56, alignment: .leading)
                PlanUsageBar(fraction: Double(free) / Double(max(reader, 1)))
                Text(formatted(free)).font(.mono(12)).foregroundStyle(MarginColors.ink).frame(width: 48, alignment: .trailing)
            }
            HStack(spacing: 10) {
                Text("Lector").font(.app(13, .bold)).foregroundStyle(MarginColors.ink).frame(width: 56, alignment: .leading)
                PlanUsageBar(fraction: 1, fill: MarginColors.yellow, track: MarginColors.yellow)
                Text(readerStorage).font(.mono(12)).foregroundStyle(MarginColors.ink).frame(width: 48, alignment: .trailing)
            }
            Text("Con Lector: \(readerStorage) en la nube").font(.app(14, .semibold)).foregroundStyle(MarginColors.ink)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(MarginColors.paper, in: RoundedRectangle(cornerRadius: 18))
        .padding(.top, 18)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Gratis \(formatted(free)), Lector \(readerStorage). Con Lector: \(readerStorage) en la nube"))
    }

    // MARK: P02b

    private func doesNotFit(bookBytes: Int64, freeBytes: Int64) -> some View {
        let size = formatted(bookBytes)
        let free = formatted(freeBytes)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                EpubPlaceholder()
                VStack(alignment: .leading, spacing: 2) {
                    Text(notice.title).font(.app(14, .bold)).foregroundStyle(MarginColors.ink).lineLimit(2)
                    Text("\(size) · te quedan \(free)").font(.mono(12)).foregroundStyle(MarginColors.warn)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
            .background(MarginColors.paper, in: RoundedRectangle(cornerRadius: 16))
            .accessibilityElement(children: .combine)
            SheetTitle(text: String(localized: "Este libro pesa \(size)")).padding(.top, 18)
            Text("Te quedan \(free) en la nube. No se subió, pero podés leerlo en este dispositivo.").sheetBody()
            HStack(spacing: 10) {
                PlanIcon(name: "UploadFile", size: 22, color: MarginColors.onYellow)
                Text("Con Lector: \(readerStorage) en la nube")
                    .font(.app(14, .semibold))
                    .foregroundStyle(MarginColors.onYellow)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(MarginColors.yellow, in: RoundedRectangle(cornerRadius: 18))
            .padding(.top, 18)
        }
    }

    /// Cada tamaño con su propia unidad ("8 MB", "1,2 GB"), con el formato de PRF-006.
    private func formatted(_ bytes: Int64) -> String {
        formatStorage(bytes, unit: StorageUnit.forTotal(bytes))
    }
}

/// La fila del libro lleva el recuadro "EPUB" del diseño (40 × 58, radio 5), no la portada, como en Android.
private struct EpubPlaceholder: View {
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 5)
        MarginColors.field
            .overlay(alignment: .bottomLeading) {
                Text(verbatim: "EPUB").font(.mono(8, .semibold)).foregroundStyle(MarginColors.muted).padding(4)
            }
            .frame(width: 40, height: 58)
            .clipShape(shape)
            .overlay(shape.strokeBorder(MarginColors.line, lineWidth: 1))
            .accessibilityHidden(true)
    }
}

extension Text {
    /// Texto de hoja: 15, interlineado 1,45, `ink2`.
    func sheetBody() -> some View {
        font(.app(15))
            .lineSpacing(15 * 0.45 - 4)
            .foregroundStyle(MarginColors.ink2)
            .fixedSize(horizontal: false, vertical: true)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 10)
    }
}
