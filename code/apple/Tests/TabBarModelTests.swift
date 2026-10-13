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

    @Test("PRF-001: Perfil abre su pantalla, sin aviso")
    func profileNavigates() {
        let model = TabBarModel()
        model.select(.profile)
        #expect(model.selected == .profile)
        #expect(!model.showsComingSoon)
    }

    @Test("HOM-006: el aviso es breve")
    func noticeHidesAfterItsDuration() async throws {
        let model = TabBarModel(noticeDuration: .milliseconds(20))
        model.showComingSoon()
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
