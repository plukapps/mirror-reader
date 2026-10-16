import Foundation
import Observation
import ReaderDomain

/// Verificar email, O3 (ONB-007, ONB-008), como `VerifyEmailViewModel` de Android.
@MainActor
@Observable
final class VerifyEmailViewModel {
    enum Next: Equatable {
        case goal
        /// Volver a crear cuenta con estos datos (ONB-008).
        case change(name: String, email: String)
    }

    static let resendSeconds = 60

    private(set) var email = ""
    private(set) var name = ""
    private(set) var resendSeconds = 0
    private(set) var busy = false
    private(set) var error: AuthError?
    private(set) var resent = false
    private(set) var next: Next?

    var canResend: Bool { resendSeconds == 0 && !busy }

    @ObservationIgnored private let auth: AuthRepository
    @ObservationIgnored private let account: AccountRepository
    @ObservationIgnored private let pollInterval: Duration
    @ObservationIgnored private let second: Duration
    @ObservationIgnored private var polling: Task<Void, Never>?
    @ObservationIgnored private var cooldown: Task<Void, Never>?
    @ObservationIgnored private var justSent: Bool

    /// - Parameter justSent: al llegar desde crear cuenta (o desde el ingreso, ONB-016) el email acaba de salir: hay
    ///   que esperar para reenviarlo.
    init(
        auth: AuthRepository,
        account: AccountRepository,
        justSent: Bool,
        pollInterval: Duration = .seconds(3),
        second: Duration = .seconds(1)
    ) {
        self.auth = auth
        self.account = account
        self.pollInterval = pollInterval
        self.second = second
        self.justSent = justSent
    }

    /// Lee el email y el nombre de la sesión y, si el email acaba de salir, arranca la espera para reenviarlo.
    func load() async {
        if justSent {
            justSent = false
            startCooldown()
        }
        let user = await account.currentUser()
        email = user?.email ?? ""
        name = user?.displayName ?? ""
    }

    /// ONB-007: mientras la pantalla está a la vista (y la app en primer plano), pregunta cada tanto si ya se verificó.
    func onVisible() {
        guard polling == nil else { return }
        polling = Task { [weak self, pollInterval] in
            while !Task.isCancelled {
                guard let self else { return }
                if (try? await self.auth.refreshEmailVerified()) == true {
                    self.next = .goal
                    self.polling = nil
                    return
                }
                try? await Task.sleep(for: pollInterval)
            }
        }
    }

    func onHidden() {
        polling?.cancel()
        polling = nil
    }

    func resend() async {
        guard canResend else { return }
        busy = true
        error = nil
        resent = false
        do {
            try await auth.sendEmailVerification()
            resent = true
            startCooldown()
        } catch {
            self.error = error.authError
        }
        busy = false
    }

    /// ONB-008: "Cambiar" y la flecha atrás. Si ya se verificó, sigue adelante en lugar de borrar la cuenta.
    func change() async {
        guard !busy else { return }
        busy = true
        error = nil
        defer { busy = false }
        if (try? await auth.refreshEmailVerified()) == true {
            next = .goal
            return
        }
        do {
            try await auth.discardUnverifiedAccount()
            onHidden()
            next = .change(name: name, email: email)
        } catch {
            self.error = error.authError
        }
    }

    func onNavigated() { next = nil }

    private func startCooldown() {
        cooldown?.cancel()
        cooldown = countdown(Self.resendSeconds, every: second) { [weak self] left in self?.resendSeconds = left }
    }
}
