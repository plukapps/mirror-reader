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

/// Hojas de Perfil (PRF-013, PLN-015).
enum ProfileSheet: Identifiable, Equatable {
    case font
    case paid(PaidFeature)

    var id: String {
        switch self {
        case .font: "font"
        case .paid: "paid"
        }
    }
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
    /// GST-009: sin sesión, Perfil invita a crear la cuenta y no muestra lo de la cuenta.
    private(set) var signedIn = true
    private(set) var canChangePassword = false
    /// nil mientras no hay ningún valor (ni leído ni guardado).
    private(set) var storage: StorageSummary?
    /// La lectura falló y no había valor guardado.
    private(set) var storageFailed = false
    let deviceName: String
    /// Ajustes del lector guardados: los mismos que cambia el panel "Aa" (PRF-011).
    private(set) var savedReading: ReaderSettings
    /// La hoja abierta en Perfil: la letra (PRF-013) o la función paga (PLN-015).
    var sheet: ProfileSheet?
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
    private let plan: @MainActor () -> PlanId

    /// `account` y `quota` son nil sin Firebase (ADR 0015).
    init(
        account: AccountRepository?, quota: QuotaSource?, cache: QuotaCache, deviceName: String,
        settings: ReaderSettingsRepository, passwords: PasswordRepository? = nil, appearance: AppearanceSettings? = nil,
        wifiOnly: SyncNetworkGate? = nil, signOut: SignOut? = nil, plan: (@MainActor () -> PlanId)? = nil
    ) {
        // PLN-015: sin dato de plan, el de la cuota guardada (desconocido: Free).
        self.plan = plan ?? { PlanCatalog.planOf(cache.last()) }
        appearanceSettings = appearance
        wifiOnlyGate = wifiOnly
        self.signOut = signOut
        self.passwords = passwords
        self.account = account
        self.quota = quota
        self.cache = cache
        self.deviceName = deviceName
        self.settings = settings
        savedReading = settings.load()
    }

    /// Lee la cuenta y el espacio: primero el guardado, después el del servidor (PRF-006).
    func load() async {
        // Pudieron cambiar en el lector desde la última vez.
        savedReading = settings.load()
        let user = await account?.currentUser()
        name = user?.displayName
        email = user?.email
        initial = user?.initial ?? ""
        canChangePassword = user?.signInMethod == .password
        // GST-009: sin cuenta (con Firebase o sin él) no se lee ni se muestra el espacio.
        signedIn = user != nil
        guard signedIn else {
            storage = nil
            storageFailed = false
            return
        }
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

    /// Lo que usa el lector (y muestra la vista previa): en Free, Clásico y serifa (PLN-015).
    var reading: ReaderSettings { effectiveReaderSettings(savedReading, plan: plan()) }

    /// PLN-015: la opción lleva la marca "Plus" y abre la hoja de función paga.
    func isLocked(_ theme: ReadingTheme) -> Bool { theme.isPaid && !plan().hasThemesAndFonts }
    func isLocked(_ font: ReaderFont) -> Bool { font.isPaid && !plan().hasThemesAndFonts }

    // PRF-011 a PRF-015: los cambios van al mismo lugar que los del panel del lector.
    func setTheme(_ theme: ReadingTheme) {
        guard !isLocked(theme) else { return showThemesAndFonts() }
        update(savedReading.withTheme(theme))
    }

    func setFont(_ font: ReaderFont) {
        guard !isLocked(font) else { return showThemesAndFonts() }
        update(savedReading.withFont(font))
    }

    func setLineSpacing(_ spacing: LineSpacing) { update(savedReading.withLineSpacing(spacing)) }
    func biggerFont() { update(savedReading.biggerFont()) }
    func smallerFont() { update(savedReading.smallerFont()) }

    func openFontSheet() { sheet = .font }

    /// Cambiar la hoja (de la letra a la función paga) cierra una y abre la otra.
    private func showThemesAndFonts() { sheet = .paid(.themesAndFonts) }

    private func update(_ newSettings: ReaderSettings) {
        guard newSettings != savedReading else { return }
        savedReading = newSettings
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
