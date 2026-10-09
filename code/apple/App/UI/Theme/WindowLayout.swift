import SwiftUI

/// Medidas de la ventana según la plataforma. En la Mac la ventana se redimensiona (MAC-001);
/// en iPhone el contenido toma el ancho de la pantalla, sin mínimo (IOS-001).
enum WindowLayout {
    /// Margen lateral de las pantallas, como en el diseño de teléfono.
    static let horizontalPadding: CGFloat = 20

    /// Ancho máximo del contenido: en la Mac o en un iPhone horizontal no se estira más.
    static let contentMaxWidth: CGFloat = 640

    #if os(macOS)
    static let minimumSize: CGSize? = CGSize(width: 480, height: 480)
    static let defaultSize = CGSize(width: 720, height: 900)
    #else
    static let minimumSize: CGSize? = nil
    #endif
}

extension View {
    /// Aplica el tamaño mínimo de la ventana donde corresponde (solo Mac).
    @ViewBuilder
    func windowMinimumSize() -> some View {
        if let size = WindowLayout.minimumSize {
            frame(minWidth: size.width, minHeight: size.height)
        } else {
            self
        }
    }
}
