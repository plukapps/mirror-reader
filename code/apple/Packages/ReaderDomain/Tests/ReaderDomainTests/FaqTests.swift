import Testing
@testable import ReaderDomain

struct FaqTests {
    @Test("PRF-017: la tarjeta de Perfil muestra estas tres, en este orden")
    func card() {
        #expect(Faq.card == [.epub, .books, .space])
    }

    @Test("PRF-018: la lista completa trae todas, una vez cada una, empezando por las de la tarjeta")
    func all() {
        #expect(Set(Faq.all).count == FaqId.allCases.count)
        #expect(Faq.all.count == FaqId.allCases.count)
        #expect(Array(Faq.all.prefix(Faq.card.count)) == Faq.card)
    }

    @Test("PRF-018: los mismos ids que Android")
    func keys() {
        #expect(FaqId.allCases.map(\.rawValue) == ["epub", "books", "space", "add", "sync", "remove"])
    }
}
