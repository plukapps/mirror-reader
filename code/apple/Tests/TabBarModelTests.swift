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

    @Test("HOM-006: un destino que aún no existe muestra el aviso y no navega", arguments: [MainDestination.search, .profile])
    func unavailableDestinationShowsNotice(destination: MainDestination) {
        let model = TabBarModel()
        model.select(destination)
        #expect(model.selected == .home)
        #expect(model.showsComingSoon)
    }

    @Test("HOM-006: el aviso es breve")
    func noticeHidesAfterItsDuration() async throws {
        let model = TabBarModel(noticeDuration: .milliseconds(20))
        model.select(.search)
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

    @Test("HOM-005: Estantes abre la biblioteca, sin aviso")
    func shelvesOpensTheLibrary() {
        let model = TabBarModel()
        model.select(.shelves)
        #expect(model.selected == .shelves)
        #expect(!model.showsComingSoon)
    }

    @Test("HOM-006: una acción que aún no existe (Importar) muestra el mismo aviso sin navegar")
    func comingSoonFromAnAction() {
        let model = TabBarModel()
        model.select(.shelves)
        model.showComingSoon()
        #expect(model.selected == .shelves)
        #expect(model.showsComingSoon)
    }
}
