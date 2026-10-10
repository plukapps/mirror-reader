import ReaderDomain
import SwiftUI

/// Texto del estado de la sincronización en la biblioteca (SYN-008), con los mismos textos que Android. Sin botón:
/// solo informa. Con un problema y sin pasada en curso, tocarlo reintenta.
struct SyncStatusLine: Equatable {
    let text: String
    let canRetry: Bool

    /// nil si no hay nada que decir: sin sesión, o todo al día.
    init?(state: SyncState, pendingUploads: Int) {
        guard state.hasSession, state.running || state.issue != nil || pendingUploads > 0 else { return nil }
        let message: String = if state.running {
            String(localized: "Sincronizando…")
        } else {
            switch state.issue {
            case .offline:
                String(localized: "Sin conexión con la nube. Tus libros siguen aquí y se sincronizarán más tarde.")
            case let .notEnoughSpace(count) where count == 1:
                String(localized: "1 libro no entra en tu espacio. Podés seguir leyendo.")
            case let .notEnoughSpace(count):
                String(localized: "\(count) libros no entran en tu espacio. Podés seguir leyendo.")
            case let .failed(count) where count == 1:
                String(localized: "No se pudo sincronizar 1 libro.")
            case let .failed(count):
                String(localized: "No se pudieron sincronizar \(count) libros.")
            case nil where pendingUploads == 1:
                String(localized: "1 libro sin subir")
            case nil:
                String(localized: "\(pendingUploads) libros sin subir")
            }
        }
        canRetry = !state.running && state.issue != nil
        text = canRetry ? String(localized: "\(message) Tocá para reintentar.") : message
    }
}

/// La línea de estado bajo el título de la biblioteca.
struct SyncStatusView: View {
    let status: SyncStatusLine
    let onRetry: () -> Void

    var body: some View {
        Button(action: onRetry) {
            Text(status.text)
                .font(.app(13))
                .foregroundStyle(MarginColors.muted)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .buttonStyle(.plain)
        .disabled(!status.canRetry)
        .accessibilityAddTraits(status.canRetry ? .isButton : .isStaticText)
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 14)
    }
}
