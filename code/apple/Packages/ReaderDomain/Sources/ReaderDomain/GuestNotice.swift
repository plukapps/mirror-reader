import Foundation

/// GST-010, GST-026: el aviso "solo en este dispositivo" se ve sin sesión, con al menos un libro, salvo en los
/// `snoozeDays` días siguientes a cerrarlo (`dismissedAt`, o nil si nunca se cerró). Días negativos valen 0.
public func shouldShowGuestNotice(signedIn: Bool, hasBooks: Bool, dismissedAt: Date?, now: Date, snoozeDays: Int) -> Bool {
    guard !signedIn, hasBooks else { return false }
    guard let dismissedAt else { return true }
    return now.timeIntervalSince(dismissedAt) >= Double(max(snoozeDays, 0)) * 86_400
}

/// GST-010: cuándo se cerró el aviso en este dispositivo, en Inicio o en la Biblioteca. Se borra al cerrar sesión
/// (ADR 0019). "Crear cuenta" no lo cierra.
public protocol GuestNoticePreference: Sendable {
    func dismissedAt() -> Date?
    func dismiss(at: Date)
}

/// GST-023: las dos opciones del banner de Inicio. GST-025 elige cuál.
public enum GuestBannerVariant: Equatable, Sendable {
    /// A: "Tus libros, en todos tus dispositivos".
    case allDevices
    /// B: "Tus libros sin respaldo".
    case noBackup
}
