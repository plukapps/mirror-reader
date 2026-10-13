import Foundation
import ReaderDomain

/// PRF-009: borra de este dispositivo lo que es de la cuenta: la base, los archivos de libros y portadas y las
/// preferencias de la cuenta (sincronización, onboarding, espacio guardado), como `LocalDataWiper` de Android.
/// Quedan los ajustes del lector (`reader.*`) y el id de esta instalación (`sync.deviceId`).
/// Cada parte se intenta aunque falle otra; al final se informa el primer error.
struct LocalAccountWiper: LocalAccountData, @unchecked Sendable {
    // `UserDefaults` es seguro entre hilos (documentación de Apple), aunque no esté marcado `Sendable`.
    let store: LibraryStore
    let files: LibraryFiles
    let defaults: UserDefaults

    /// Claves de `UserDefaults` que son de la cuenta. A diferencia de Android, aquí se listan las que se borran: el
    /// dominio de la app también guarda claves del sistema que no se deben tocar. Una clave nueva de la cuenta se suma aquí.
    static let accountKeys = [
        "onboardingInterests", "readingGoalMinutes", "reminderEnabled", "reminderHour", "reminderMinute",
        "sync.lastPositionsSeenAt",
        UserDefaultsQuotaCache.Key.usedBytes, UserDefaultsQuotaCache.Key.quotaBytes, UserDefaultsQuotaCache.Key.plan,
    ]

    func wipe() async throws {
        var firstError: Error?
        do { try await store.deleteAll() } catch { firstError = firstError ?? error }
        do { try files.deleteAll() } catch { firstError = firstError ?? error }
        Self.accountKeys.forEach(defaults.removeObject(forKey:))
        if let firstError { throw firstError }
    }
}
