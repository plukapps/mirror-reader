import Foundation
import ReaderDomain

/// Último espacio leído (PRF-006), con las mismas claves que el DataStore de Android.
struct UserDefaultsQuotaCache: QuotaCache, @unchecked Sendable {
    // `UserDefaults` es seguro entre hilos (documentación de Apple), aunque no esté marcado `Sendable`.
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func last() -> StorageQuota? {
        guard let used = defaults.object(forKey: Key.usedBytes) as? Int64,
              let quota = defaults.object(forKey: Key.quotaBytes) as? Int64
        else { return nil }
        return StorageQuota(usedBytes: used, quotaBytes: quota, plan: defaults.string(forKey: Key.plan) ?? StorageQuota.freePlan)
    }

    func save(_ quota: StorageQuota) {
        defaults.set(quota.usedBytes, forKey: Key.usedBytes)
        defaults.set(quota.quotaBytes, forKey: Key.quotaBytes)
        defaults.set(quota.plan, forKey: Key.plan)
    }

    enum Key {
        static let usedBytes = "profile_used_bytes"
        static let quotaBytes = "profile_quota_bytes"
        static let plan = "profile_plan"
    }
}
