import Foundation

/// Dónde se registran los cierres inesperados (CMP-010, ADR 0018).
@MainActor
public protocol CrashReporter: AnyObject {
    /// Identificador de la cuenta que acompaña a los próximos reportes; vacío para ninguna.
    func setUserID(_ id: String)
}

/// CMP-010: los reportes de cierre llevan el `uid` de la cuenta (nunca el email ni el nombre) y, sin sesión (PRF-009),
/// ningún identificador. Como `CrashReportIdentity` de Android.
@MainActor
public final class CrashReportIdentity {
    private let reporter: CrashReporter
    private var last: String?

    public init(reporter: CrashReporter) {
        self.reporter = reporter
    }

    /// Cada cambio de sesión. No repite el mismo identificador.
    public func update(userId: String?) {
        let id = userId ?? ""
        guard id != last else { return }
        last = id
        reporter.setUserID(id)
    }
}
