import Foundation
import Testing
@testable import Reader

struct HomeFormattingTests {
    @Test("HOM-010: apellido del primer autor", arguments: [
        ("F. S. Fitzgerald", "Fitzgerald"),
        ("Peter Thiel, Blake Masters", "Thiel"),
        ("Homero", "Homero"),
        ("  Jane Austen  ", "Austen"),
    ])
    func shortAuthorTakesFirstAuthorsLastName(author: String, expected: String) {
        #expect(shortAuthor(author) == expected)
    }

    @Test("HOM-010: mes abreviado con mayúscula y sin punto")
    func monthLabelIsShortCapitalizedWithoutDot() {
        let september = Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: 15))!
        #expect(monthLabel(september, locale: Locale(identifier: "es_AR")) == "Sept")
        #expect(monthLabel(september, locale: Locale(identifier: "en_US")) == "Sep")
    }

    @Test("HOM-010: subtítulo de Terminados, Apellido · Mes")
    func finishedSubtitleJoinsAuthorAndMonth() {
        let september = Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: 15))!
        let locale = Locale(identifier: "en_US")
        #expect(finishedSubtitle(author: "F. S. Fitzgerald", lastReadAt: september, locale: locale) == "Fitzgerald · Sep")
        #expect(finishedSubtitle(author: nil, lastReadAt: september, locale: locale) == "Sep")
        #expect(finishedSubtitle(author: "Homero", lastReadAt: nil, locale: locale) == "Homero")
        #expect(finishedSubtitle(author: nil, lastReadAt: nil, locale: locale) == nil)
    }
}
