import Testing
@testable import ReaderDomain

@Suite("Identidad de los reportes de cierre")
@MainActor
struct CrashReportIdentityTests {
    final class FakeReporter: CrashReporter {
        var ids: [String] = []
        func setUserID(_ id: String) { ids.append(id) }
    }

    @Test("CMP-010: con sesión, los reportes llevan el uid")
    func sessionSendsUid() {
        let reporter = FakeReporter()
        let identity = CrashReportIdentity(reporter: reporter)

        identity.update(userId: "u1")

        #expect(reporter.ids == ["u1"])
    }

    @Test("CMP-010: al cerrar sesión se borra, y el mismo uid no se repite")
    func signOutClears() {
        let reporter = FakeReporter()
        let identity = CrashReportIdentity(reporter: reporter)

        identity.update(userId: nil)
        identity.update(userId: "u1")
        identity.update(userId: "u1")
        identity.update(userId: nil)

        #expect(reporter.ids == ["", "u1", ""])
    }
}
