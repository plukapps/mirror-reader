import Testing
@testable import ReaderDomain

@Suite("Notas")
struct NotesTests {
    private func at(_ progression: Double) -> Note {
        Note(id: "n", bookId: "b", locatorJson: "{}", quote: "q", text: "t", progression: progression, createdAt: 0, updatedAt: 0)
    }

    @Test("ANN-010: el porcentaje del libro donde está la nota")
    func percent() {
        #expect(at(0).percent == 0)
        #expect(at(0.427).percent == 42)
        #expect(at(1).percent == 100)
        #expect(at(1.3).percent == 100)
    }
}
