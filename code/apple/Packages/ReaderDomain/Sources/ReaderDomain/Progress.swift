/// Porcentaje 0...100 del libro leído, o nil si todavía no hay dato (RDR-005). Igual que Android.
public func progressPercent(_ totalProgression: Double?) -> Int? {
    guard let totalProgression else { return nil }
    return min(max(Int((totalProgression * 100).rounded()), 0), 100)
}

/// Gana la lectura más reciente por `readAt` (SYN-003). Con el mismo `readAt` se conserva la local.
public func shouldUseRemotePosition(localReadAt: Int64?, remoteReadAt: Int64) -> Bool {
    guard let localReadAt else { return true }
    return remoteReadAt > localReadAt
}
