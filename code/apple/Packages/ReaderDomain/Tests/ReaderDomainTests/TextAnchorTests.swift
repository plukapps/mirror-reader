import Testing
@testable import ReaderDomain

// SYN-014: la posición recuerda el texto del comienzo de la página. Mismos casos que `TextAnchorTest` de Android.

struct TextAnchorTests {
    private let before = String(repeating: "a", count: 40)
    private let highlight = String(repeating: "b", count: 32)
    private let after = String(repeating: "c", count: 40)

    @Test func takesThirtyTwoCharactersOfContextOnEachSide() {
        let anchor = textAnchorAt(window: before + highlight + after, start: 40)
        #expect(anchor == TextAnchor(
            before: String(repeating: "a", count: 32),
            highlight: highlight,
            after: String(repeating: "c", count: 32)
        ))
    }

    // El primer carácter visible puede ser un espacio o un salto de línea entre párrafos
    @Test func startsAtTheFirstVisibleCharacterAfterWhitespace() {
        let anchor = textAnchorAt(window: "fin.\n  \tY entonces la puerta", start: 4)
        #expect(anchor?.highlight == "Y entonces la puerta")
        #expect(anchor?.before == "fin.\n  \t")
        #expect(anchor?.after == nil)
    }

    @Test func atTheStartOfTheChapterThereIsNoBefore() {
        #expect(textAnchorAt(window: "Capítulo uno", start: 0) == TextAnchor(before: nil, highlight: "Capítulo uno", after: nil))
    }

    @Test func withoutVisibleTextThereIsNoAnchor() {
        #expect(textAnchorAt(window: "texto   \n ", start: 5) == nil)
        #expect(textAnchorAt(window: "texto", start: 5) == nil)
        #expect(textAnchorAt(window: "texto", start: -1) == nil)
        #expect(textAnchorAt(window: "", start: 0) == nil)
    }

    // Los índices son UTF-16, como en JavaScript, y los cortes no parten un emoji
    @Test func cutsDoNotSplitSurrogatePairs() {
        let window = "😀" + String(repeating: "a", count: 31) + String(repeating: "b", count: 31) + "😀" + "c"
        let anchor = textAnchorAt(window: window, start: 33)
        #expect(anchor?.before == String(repeating: "a", count: 31))
        #expect(anchor?.highlight == String(repeating: "b", count: 31) + "😀")
        #expect(anchor?.after == "c")
    }
}
