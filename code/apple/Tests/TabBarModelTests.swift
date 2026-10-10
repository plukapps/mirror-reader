import Testing
@testable import Reader

@MainActor
struct TabBarModelTests {
    @Test("HOM-005: Inicio es el destino de arranque")
    func startsAtHome() {
        let model = TabBarModel()
        #expect(model.selected == .home)
        #expect(!model.showsComingSoon)
    }

    @Test("HOM-005: los cuatro destinos, en el orden del diseño")
    func destinationsInDesignOrder() {
        #expect(MainDestination.allCases == [.home, .search, .shelves, .profile])
    }

    @Test("HOM-006: un destino que aún no existe muestra el aviso y no navega", arguments: [MainDestination.shelves, .profile])
    func unavailableDestinationShowsNotice(destination: MainDestination) {
        let model = TabBarModel()
        model.select(destination)
        #expect(model.selected == .home)
        #expect(model.showsComingSoon)
    }

    @Test("HOM-006: el aviso es breve")
    func noticeHidesAfterItsDuration() async throws {
        let model = TabBarModel(noticeDuration: .milliseconds(20))
        model.select(.profile)
        try await Task.sleep(for: .milliseconds(200))
        #expect(!model.showsComingSoon)
    }

    @Test("HOM-005: tocar Inicio no muestra aviso")
    func selectingHomeShowsNoNotice() {
        let model = TabBarModel()
        model.select(.home)
        #expect(model.selected == .home)
        #expect(!model.showsComingSoon)
    }

    @Test("HOM-005: Buscar navega a la búsqueda, sin aviso")
    func selectingSearchNavigates() {
        let model = TabBarModel()
        model.select(.search)
        #expect(model.selected == .search)
        #expect(!model.showsComingSoon)

        model.select(.home)
        #expect(model.selected == .home)
    }
}
