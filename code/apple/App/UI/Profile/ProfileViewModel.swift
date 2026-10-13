import Foundation
import ReaderDomain

/// Espacio listo para mostrar (PRF-006): tamaños en la unidad del total y proporción usada entre 0 y 1.
struct StorageSummary: Equatable {
    let used: String
    let total: String
    let free: String
    let fraction: Double
    let isFreePlan: Bool
}

/// Perfil (PRF-001 a PRF-019), como `ProfileViewModel` de Android.
@MainActor @Observable
final class ProfileViewModel {
    /// Nombre de la cuenta, o nil si no tiene: entonces la cabecera muestra solo el email (PRF-005).
    private(set) var name: String?
    private(set) var email: String?
    private(set) var initial = ""
    private(set) var canChangePassword = false
    /// nil mientras no hay ningún valor (ni leído ni guardado).
    private(set) var storage: StorageSummary?
    /// La lectura falló y no había valor guardado.
    private(set) var storageFailed = false
    let deviceName: String

    private let account: AccountRepository?
    private let quota: QuotaSource?
    private let cache: QuotaCache

    /// `account` y `quota` son nil sin Firebase (ADR 0015).
    init(account: AccountRepository?, quota: QuotaSource?, cache: QuotaCache, deviceName: String) {
        self.account = account
        self.quota = quota
        self.cache = cache
        self.deviceName = deviceName
    }

    /// Lee la cuenta y el espacio: primero el guardado, después el del servidor (PRF-006).
    func load() async {
        let user = await account?.currentUser()
        name = user?.displayName
        email = user?.email
        initial = (user?.displayName ?? user?.email)?.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? ""
        canChangePassword = user?.signInMethod == .password
        if storage == nil, let saved = cache.last() { storage = summary(saved) }
        guard let quota else {
            storageFailed = storage == nil
            return
        }
        do {
            let current = try await quota.current()
            cache.save(current)
            storage = summary(current)
            storageFailed = false
        } catch {
            storageFailed = storage == nil
        }
    }

    private func summary(_ quota: StorageQuota) -> StorageSummary {
        let unit = StorageUnit.forTotal(quota.quotaBytes)
        return StorageSummary(
            used: formatStorage(quota.usedBytes, unit: unit),
            total: formatStorage(quota.quotaBytes, unit: unit),
            free: formatStorage(quota.freeBytes, unit: unit),
            fraction: quota.quotaBytes <= 0 ? 0 : min(Double(quota.usedBytes) / Double(quota.quotaBytes), 1),
            isFreePlan: quota.isFreePlan
        )
    }
}
