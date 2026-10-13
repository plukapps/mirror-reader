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

/// PRF-009: la confirmación avisa si hay cambios de este dispositivo que se perderían.
struct SignOutDialog: Equatable {
    let hasPendingChanges: Bool
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
    /// Ajustes del lector: los mismos que cambia el panel "Aa" (PRF-011).
    private(set) var reading: ReaderSettings
    /// Confirmación de cerrar sesión a la vista, o nil (PRF-009).
    private(set) var signOutDialog: SignOutDialog?
    private(set) var signingOut = false
    /// La sesión se cerró: la app vuelve a la bienvenida.
    private(set) var signedOut = false
    /// El último intento de cerrar sesión falló.
    var signOutFailed = false

    private let account: AccountRepository?
    private let quota: QuotaSource?
    private let cache: QuotaCache
    private let settings: ReaderSettingsRepository
    private let signOut: SignOut?
    private let passwords: PasswordRepository?
    private let appearanceSettings: AppearanceSettings?
    private let wifiOnlyGate: SyncNetworkGate?

    /// `account` y `quota` son nil sin Firebase (ADR 0015).
    init(
        account: AccountRepository?, quota: QuotaSource?, cache: QuotaCache, deviceName: String,
        settings: ReaderSettingsRepository, passwords: PasswordRepository? = nil, appearance: AppearanceSettings? = nil,
        wifiOnly: SyncNetworkGate? = nil, signOut: SignOut? = nil
    ) {
        appearanceSettings = appearance
        wifiOnlyGate = wifiOnly
        self.signOut = signOut
        self.passwords = passwords
        self.account = account
        self.quota = quota
        self.cache = cache
        self.deviceName = deviceName
        self.settings = settings
        reading = settings.load()
    }

    /// Lee la cuenta y el espacio: primero el guardado, después el del servidor (PRF-006).
    func load() async {
        // Pudieron cambiar en el lector desde la última vez.
        reading = settings.load()
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

    /// PLN-012: Mi plan, con la misma cuota (guardada y del servidor).
    func makeMyPlan() -> MyPlanViewModel {
        MyPlanViewModel(quota: quota, cache: cache)
    }

    /// Hay con qué cambiar la contraseña (con Firebase).
    var hasChangePassword: Bool { passwords != nil }

    /// PRF-020: la pantalla de cambiar la contraseña; nil sin cuenta (sin Firebase).
    func makeChangePassword() -> ChangePasswordViewModel? {
        passwords.map { ChangePasswordViewModel(passwords: $0) }
    }

    /// PRF-021: la apariencia elegida; nil si la app no la maneja (avisa que llega más adelante).
    var appearance: Appearance? { appearanceSettings?.appearance }

    func setAppearance(_ appearance: Appearance) {
        appearanceSettings?.select(appearance)
    }

    /// PRF-023: "Sincronizar solo con Wi-Fi", o nil si la app no lo maneja (avisa que llega más adelante).
    var wifiOnly: Bool? { wifiOnlyGate?.wifiOnly }

    func setWifiOnly(_ on: Bool) {
        wifiOnlyGate?.setWifiOnly(on)
    }

    /// Sin cuenta (sin Firebase) no hay sesión que cerrar.
    var canSignOut: Bool { signOut != nil }

    /// PRF-009: muestra la confirmación, con aviso si hay cambios sin subir.
    func requestSignOut() async {
        guard let signOut, !signingOut else { return }
        signOutDialog = SignOutDialog(hasPendingChanges: await signOut.hasPendingChanges())
    }

    func dismissSignOut() {
        signOutDialog = nil
    }

    func confirmSignOut() async {
        guard let signOut, !signingOut else { return }
        signOutDialog = nil
        signingOut = true
        do {
            try await signOut()
            signedOut = true
        } catch {
            signOutFailed = true
        }
        signingOut = false
    }

    // PRF-011 a PRF-015: los cambios van al mismo lugar que los del panel del lector.
    func setTheme(_ theme: ReadingTheme) { update(reading.withTheme(theme)) }
    func setFont(_ font: ReaderFont) { update(reading.withFont(font)) }
    func setLineSpacing(_ spacing: LineSpacing) { update(reading.withLineSpacing(spacing)) }
    func biggerFont() { update(reading.biggerFont()) }
    func smallerFont() { update(reading.smallerFont()) }

    private func update(_ newSettings: ReaderSettings) {
        guard newSettings != reading else { return }
        reading = newSettings
        settings.save(newSettings)
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
