/// Porcentaje 0...100 del libro leído, o nil si todavía no hay dato (RDR-005). Igual que Android.
public func progressPercent(_ totalProgression: Double?) -> Int? {
    guard let totalProgression else { return nil }
    return min(max(Int((totalProgression * 100).rounded()), 0), 100)
}
