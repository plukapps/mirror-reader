import SwiftUI

/// Destinos de la barra inferior (HOM-005), en el mismo orden que Android.
enum MainDestination: CaseIterable, Identifiable {
    case home, search, shelves, profile

    var id: Self { self }

    var label: LocalizedStringKey {
        switch self {
        case .home: "Inicio"
        case .search: "Buscar"
        case .shelves: "Estantes"
        case .profile: "Perfil"
        }
    }

    /// Íconos de Material Symbols Rounded en `Assets.xcassets/TabBar`, los mismos trazados que Android.
    var icon: String {
        switch self {
        case .home: "NavHome"
        case .search: "NavSearch"
        case .shelves: "NavShelves"
        case .profile: "NavPerson"
        }
    }

    /// Relleno (`FILL 1`) para el destino activo. La lupa no cambia.
    var selectedIcon: String {
        switch self {
        case .home: "NavHomeFilled"
        case .search: "NavSearch"
        case .shelves: "NavShelvesFilled"
        case .profile: "NavPersonFilled"
        }
    }

    /// En Apple existen Inicio, Buscar y la biblioteca (Estantes, como en Android). Perfil, después.
    var isAvailable: Bool { self != .profile }
}

/// Estado de la barra: destino actual y el aviso de los destinos que aún no existen (HOM-006).
@MainActor @Observable
final class TabBarModel {
    private(set) var selected: MainDestination = .home
    private(set) var showsComingSoon = false

    private let noticeDuration: Duration
    private var noticeTask: Task<Void, Never>?

    init(noticeDuration: Duration = .seconds(2)) {
        self.noticeDuration = noticeDuration
    }

    func select(_ destination: MainDestination) {
        guard destination.isAvailable else { return showComingSoon() }
        selected = destination
    }

    /// Aviso de HOM-006, también para acciones que aún no existen (Importar).
    /// Un toque más reinicia el tiempo del aviso en lugar de apilar otro.
    func showComingSoon() {
        showsComingSoon = true
        noticeTask?.cancel()
        noticeTask = Task { [noticeDuration] in
            try? await Task.sleep(for: noticeDuration)
            guard !Task.isCancelled else { return }
            showsComingSoon = false
        }
    }
}
