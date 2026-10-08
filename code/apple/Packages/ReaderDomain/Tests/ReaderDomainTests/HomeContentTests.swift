import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `HomeContentTest` de Android: el spec es el contrato común (ADR 0001).
struct HomeContentTests {
    private func book(_ id: String, _ progress: Int?, lastReadAt: Double? = nil, addedAt: Double = 0) -> LibraryBook {
        LibraryBook(
            id: id,
            title: "T\(id)",
            author: nil,
            coverPath: nil,
            progressPercent: progress,
            lastReadAt: lastReadAt.map(Date.init(timeIntervalSince1970:)),
            addedAt: Date(timeIntervalSince1970: addedAt)
        )
    }

    @Test("HOM-002: el de hoy gana al de ayer")
    func continueReadingIsTheMostRecentlyReadBook() {
        let content = homeContent([book("ayer", 42, lastReadAt: 100), book("hoy", 10, lastReadAt: 200)])
        #expect(content.continueReading?.id == "hoy")
    }

    @Test("HOM-002: en empate gana el primero de la lista (el importado más reciente)")
    func tieOnLastReadKeepsLibraryOrder() {
        let content = homeContent([book("a", 5, lastReadAt: 100), book("b", 5, lastReadAt: 100)])
        #expect(content.continueReading?.id == "a")
    }

    @Test("HOM-002: al 100 % o sin abrir no es Continuar leyendo")
    func finishedAndNewBooksAreNotContinueReading() {
        let content = homeContent([book("fin", 100, lastReadAt: 300), book("nuevo", nil)])
        #expect(content.continueReading == nil)
    }

    @Test("HOM-003: la biblioteca vacía se marca")
    func emptyLibraryIsFlagged() {
        #expect(homeContent([]).libraryEmpty)
        #expect(!homeContent([book("nuevo", nil)]).libraryEmpty)
    }

    @Test("HOM-008: Leyendo excluye Continuar leyendo, más reciente primero, con total")
    func readingExcludesContinueReadingAndKeepsTotal() {
        let content = homeContent([
            book("a", 10, lastReadAt: 1), book("b", 20, lastReadAt: 3), book("c", 30, lastReadAt: 2), book("nuevo", nil),
        ])
        #expect(content.continueReading?.id == "b")
        #expect(content.reading.map(\.id) == ["c", "a"])
        #expect(content.readingCount == 2)
    }

    @Test("HOM-008: con un solo libro en lectura la sección queda vacía")
    func readingIsEmptyWithASingleBookInProgress() {
        let content = homeContent([book("a", 10, lastReadAt: 1)])
        #expect(content.reading.isEmpty)
        #expect(content.readingCount == 0)
    }

    @Test("HOM-008 a HOM-010: máximo 5 por fila, el total cuenta todos")
    func rowsAreLimitedToFiveButCountsAreTotals() {
        let reading = (1...8).map { book("r\($0)", 10, lastReadAt: Double($0), addedAt: Double($0)) }
        let finished = (1...7).map { book("f\($0)", 100, lastReadAt: Double($0), addedAt: 100 + Double($0)) }
        let content = homeContent(reading + finished)
        #expect(content.reading.count == 5)
        #expect(content.readingCount == 7)
        #expect(content.finished.count == 5)
        #expect(content.finishedCount == 7)
        #expect(content.recentlyAdded.count == 5)
        #expect(content.recentlyAddedCount == 15)
    }

    @Test("HOM-009: el último importado primero, de cualquier estado")
    func recentlyAddedIsNewestImportFirstAnyStatus() {
        let content = homeContent([
            book("viejo", 100, lastReadAt: 9, addedAt: 1), book("nuevo", nil, addedAt: 5), book("medio", 40, lastReadAt: 2, addedAt: 3),
        ])
        #expect(content.recentlyAdded.map(\.id) == ["nuevo", "medio", "viejo"])
    }

    @Test("HOM-010: solo terminados, el último leído primero")
    func finishedHasOnlyFinishedBooksMostRecentFirst() {
        let content = homeContent([
            book("f1", 100, lastReadAt: 1), book("leyendo", 42, lastReadAt: 9), book("f2", 100, lastReadAt: 5), book("nuevo", nil),
        ])
        #expect(content.finished.map(\.id) == ["f2", "f1"])
    }

    @Test("HOM-001: el saludo sigue la hora del día", arguments: [
        (4, Greeting.night), (5, .morning), (11, .morning), (12, .afternoon), (19, .afternoon), (20, .night), (0, .night),
    ])
    func greetingFollowsHourOfDay(hour: Int, expected: Greeting) {
        #expect(greeting(forHour: hour) == expected)
    }

    @Test("LIB-010: el estado sale del progreso", arguments: [
        (nil, ReadingStatus.new), (0, .reading), (42, .reading), (99, .reading), (100, .finished),
    ] as [(Int?, ReadingStatus)])
    func statusFollowsProgress(progress: Int?, expected: ReadingStatus) {
        #expect(readingStatus(progressPercent: progress) == expected)
    }
}
