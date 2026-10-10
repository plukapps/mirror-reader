import Foundation
import ReaderDomain
#if os(iOS)
import UIKit
#endif

/// Datos de este dispositivo para sincronizar la posición (SYN-011, SYN-012), en `UserDefaults`, como
/// `SyncPreferencesImpl` de Android.
struct UserDefaultsSyncPreferences: SyncPreferences, @unchecked Sendable {
    // `UserDefaults` es seguro entre hilos, aunque no se declare `Sendable`.
    private let defaults: UserDefaults
    private let name: String

    /// - Parameter deviceName: nombre legible; en la app, `model`, el modelo del equipo ("iPhone", "iPad", "Mac"). El
    ///   nombre que el usuario le puso al equipo pide un permiso especial desde iOS 16, así que no se usa.
    init(defaults: UserDefaults = .standard, deviceName: String) {
        self.defaults = defaults
        let trimmed = deviceName.trimmingCharacters(in: .whitespaces)
        // La nube acepta de 1 a 64 caracteres (`backend.md`).
        name = String((trimmed.isEmpty ? Self.fallbackName : trimmed).prefix(64))
    }

    func deviceId() async -> String {
        if let id = defaults.string(forKey: Key.deviceId) { return id }
        let id = UUID().uuidString.lowercased()
        defaults.set(id, forKey: Key.deviceId)
        return id
    }

    func deviceName() -> String { name }

    func lastPositionsSeenAt() async -> Int64? {
        (defaults.object(forKey: Key.lastPositionsSeenAt) as? NSNumber)?.int64Value
    }

    func setLastPositionsSeenAt(_ value: Int64) async {
        defaults.set(NSNumber(value: value), forKey: Key.lastPositionsSeenAt)
    }

    private enum Key {
        static let deviceId = "sync.deviceId"
        static let lastPositionsSeenAt = "sync.lastPositionsSeenAt"
    }

    #if os(iOS)
    static let fallbackName = "iPhone"
    @MainActor static var model: String { UIDevice.current.model }
    #else
    static let fallbackName = "Mac"
    static var model: String { "Mac" }
    #endif
}
