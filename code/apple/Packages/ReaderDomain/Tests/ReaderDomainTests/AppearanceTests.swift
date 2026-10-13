import Testing
@testable import ReaderDomain

struct AppearanceTests {
    @Test("PRF-021: por defecto, Sistema")
    func defaultIsSystem() {
        #expect(Appearance.default == .system)
    }

    @Test("PRF-021: se guarda con los mismos valores que Android")
    func storedValues() {
        #expect(Appearance.light.rawValue == "LIGHT")
        #expect(Appearance.dark.rawValue == "DARK")
        #expect(Appearance.system.rawValue == "SYSTEM")
        #expect(Appearance(stored: "DARK") == .dark)
        #expect(Appearance(stored: nil) == .system)
        #expect(Appearance(stored: "otro") == .system)
    }
}
