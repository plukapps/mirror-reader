/// Preguntas frecuentes (PRF-017, PRF-018), con los mismos ids que Android. Viven en la app: se leen sin conexión.
/// Los textos están en la UI.
public enum FaqId: String, CaseIterable, Hashable, Sendable {
    case epub, books, space, add, sync, remove
}

public enum Faq {
    /// Las tres de la tarjeta de ayuda de Perfil, en el orden del diseño.
    public static let card: [FaqId] = [.epub, .books, .space]

    /// "Todas las preguntas": primero las de la tarjeta, después el resto.
    public static let all: [FaqId] = card + FaqId.allCases.filter { !card.contains($0) }
}
