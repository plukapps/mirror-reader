import Foundation
import ReaderDomain
import Testing
@testable import Reader

private final class StubPasswords: PasswordRepository, @unchecked Sendable {
    var failure: AuthError?
    var changes: [(current: String, new: String)] = []
    func changePassword(current: String, new: String) async throws {
        changes.append((current, new))
        if let failure { throw AuthFailure(failure) }
    }
}

@MainActor
private func filled(_ passwords: StubPasswords) -> ChangePasswordViewModel {
    let viewModel = ChangePasswordViewModel(passwords: passwords)
    viewModel.current = "vieja123"
    viewModel.password = "Nueva-123"
    viewModel.confirm = "Nueva-123"
    return viewModel
}

@MainActor
struct ChangePasswordViewModelTests {
    @Test("PRF-020: Guardar se habilita con los tres campos bien")
    func enabling() {
        let viewModel = ChangePasswordViewModel(passwords: StubPasswords())
        #expect(!viewModel.canSave)
        viewModel.current = "vieja123"
        viewModel.password = "Nueva-123"
        #expect(!viewModel.canSave)
        viewModel.confirm = "Nueva-12"
        #expect(viewModel.mismatch)
        #expect(!viewModel.canSave)
        viewModel.confirm = "Nueva-123"
        #expect(!viewModel.mismatch)
        #expect(viewModel.canSave)
    }

    @Test("PRF-020: guardar cambia la contraseña y termina")
    func saves() async {
        let passwords = StubPasswords()
        let viewModel = filled(passwords)
        await viewModel.save()
        #expect(passwords.changes.count == 1)
        #expect(passwords.changes.first?.current == "vieja123")
        #expect(passwords.changes.first?.new == "Nueva-123")
        #expect(viewModel.saved)
        #expect(!viewModel.busy)
    }

    @Test("PRF-020: con la actual incorrecta lo dice bajo ese campo y no termina")
    func wrongCurrent() async {
        let passwords = StubPasswords()
        passwords.failure = .invalidCredentials
        let viewModel = filled(passwords)
        await viewModel.save()
        #expect(viewModel.currentError == "La contraseña actual no es correcta.")
        #expect(viewModel.error == nil)
        #expect(!viewModel.saved)
        // Al corregirla, el aviso se va.
        viewModel.current = "vieja1234"
        #expect(viewModel.currentError == nil)
    }

    @Test("PRF-020: sin conexión y demasiados intentos se avisan como en ONB-015")
    func otherErrors() async {
        let passwords = StubPasswords()
        passwords.failure = .network
        let viewModel = filled(passwords)
        await viewModel.save()
        #expect(viewModel.error == .network)
        #expect(viewModel.currentError == nil)
        #expect(!viewModel.saved)

        passwords.failure = .tooManyRequests
        await viewModel.save()
        #expect(viewModel.error == .tooManyRequests)
    }

    @Test("PRF-020: sin los datos completos no llama al servidor")
    func doesNothingWhenDisabled() async {
        let passwords = StubPasswords()
        let viewModel = ChangePasswordViewModel(passwords: passwords)
        viewModel.current = "vieja123"
        await viewModel.save()
        #expect(passwords.changes.isEmpty)
    }
}
