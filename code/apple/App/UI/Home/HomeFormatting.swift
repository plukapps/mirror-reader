import Foundation

/// Apellido del primer autor ("Peter Thiel, Blake Masters" → "Thiel"), como en el diseño: "Fitzgerald · Sep".
func shortAuthor(_ author: String) -> String {
    let first = author.split(separator: ",", maxSplits: 1).first.map(String.init) ?? author
    let trimmed = first.trimmingCharacters(in: .whitespaces)
    return trimmed.split(separator: " ").last.map(String.init) ?? trimmed
}

/// Mes abreviado en el idioma dado, con mayúscula inicial y sin punto (HOM-010).
func monthLabel(_ date: Date, locale: Locale = .current) -> String {
    let formatter = DateFormatter()
    formatter.locale = locale
    formatter.setLocalizedDateFormatFromTemplate("MMM")
    let month = formatter.string(from: date).trimmingCharacters(in: CharacterSet(charactersIn: "."))
    return month.prefix(1).uppercased(with: locale) + month.dropFirst()
}

/// Subtítulo de un libro en "Terminados": "Apellido · Mes", o lo que haya de los dos (HOM-010).
func finishedSubtitle(author: String?, lastReadAt: Date?, locale: Locale = .current) -> String? {
    let parts = [author.map(shortAuthor), lastReadAt.map { monthLabel($0, locale: locale) }].compactMap { $0 }
    return parts.isEmpty ? nil : parts.joined(separator: " · ")
}
