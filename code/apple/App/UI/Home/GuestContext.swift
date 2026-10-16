import Foundation
import ReaderDomain

/// Lo que necesitan Inicio y la Biblioteca para el uso sin cuenta (GST-010, GST-023, GST-025, GST-026, HOM-012).
/// nil donde no hay cuenta posible (sin Firebase): ni aviso ni banner.
struct GuestContext: Sendable {
    let account: AccountRepository
    let notice: GuestNoticePreference
    let flags: FeatureFlags?
    var now: @Sendable () -> Date = { Date() }

    /// El usuario actual, si el aviso corresponde (con al menos un libro) y qué opción del banner usar.
    func state(hasBooks: Bool) async -> (user: AccountUser?, showNotice: Bool, variant: GuestBannerVariant) {
        let user = await account.currentUser()
        let days = await flags?.setting(.guestNoticeSnoozeDays) ?? Setting.guestNoticeSnoozeDays.defaultValue
        let show = shouldShowGuestNotice(
            signedIn: user != nil, hasBooks: hasBooks, dismissedAt: notice.dismissedAt(), now: now(), snoozeDays: days
        )
        let noBackup = await flags?.isEnabled(.guestBannerNoBackup) ?? Feature.guestBannerNoBackup.defaultValue
        return (user, show, noBackup ? .noBackup : .allDevices)
    }

    /// La cruz lo esconde por unos días en Inicio y en la Biblioteca. "Crear cuenta" no lo esconde.
    func dismiss() { notice.dismiss(at: now()) }
}
