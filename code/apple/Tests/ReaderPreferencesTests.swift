#if os(iOS)
@preconcurrency import ReadiumNavigator
import ReaderDomain
import Testing
@testable import Reader

/// Ajustes de lectura traducidos a Readium, como `EpubPreferencesMapperTest` de Android (RDR-001 a RDR-003, RDR-014).
struct ReaderPreferencesTests {
    @Test("RDR-001, RDR-014: siempre paginado, una columna, y la letra y el interlineado del usuario mandan")
    func mapsSettings() {
        let preferences = EPUBPreferences(ReaderSettings(theme: .sepia, fontScale: 1.3, font: .mono, lineSpacing: .wide))

        #expect(preferences.scroll == false)
        #expect(preferences.columnCount == .one)
        #expect(preferences.publisherStyles == false)
        #expect(preferences.theme == .sepia)
        #expect(preferences.fontFamily?.rawValue == "JetBrains Mono")
        #expect(preferences.fontSize == 1.3)
        #expect(preferences.lineHeight == 1.7)
    }

    @Test("RDR-003: cada tema del panel es un tema de Readium")
    func mapsThemes() {
        #expect(EPUBPreferences(ReaderSettings(theme: .light)).theme == .light)
        #expect(EPUBPreferences(ReaderSettings(theme: .dark)).theme == .dark)
    }
}
#endif
