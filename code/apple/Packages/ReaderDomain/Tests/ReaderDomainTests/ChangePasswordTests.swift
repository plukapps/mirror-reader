import Testing
@testable import ReaderDomain

struct ChangePasswordTests {
    @Test("PRF-020: Guardar se habilita con la actual, una nueva válida, distinta y confirmada")
    func enabledWhenEverythingIsRight() {
        #expect(canSaveNewPassword(current: "vieja123", new: "Nueva-123", confirm: "Nueva-123"))
    }

    @Test("PRF-020: sin la contraseña actual no se puede guardar")
    func needsCurrent() {
        #expect(!canSaveNewPassword(current: "", new: "Nueva-123", confirm: "Nueva-123"))
    }

    @Test("PRF-020: la nueva tiene que cumplir ONB-005")
    func newMustBeValid() {
        #expect(!canSaveNewPassword(current: "vieja123", new: "nueva123", confirm: "nueva123"))
        #expect(!canSaveNewPassword(current: "vieja123", new: "N1-", confirm: "N1-"))
    }

    @Test("PRF-020: la nueva tiene que ser distinta de la actual")
    func newMustDiffer() {
        #expect(!canSaveNewPassword(current: "Nueva-123", new: "Nueva-123", confirm: "Nueva-123"))
    }

    @Test("PRF-020: la confirmación tiene que coincidir")
    func confirmMustMatch() {
        #expect(!canSaveNewPassword(current: "vieja123", new: "Nueva-123", confirm: "Nueva-124"))
        #expect(!canSaveNewPassword(current: "vieja123", new: "Nueva-123", confirm: ""))
    }
}
