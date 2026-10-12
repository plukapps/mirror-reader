import Foundation

/// Cuenta regresiva de reenvío (ONB-007, ONB-017), como `countdown` de Android: avisa `seconds` en el acto y después
/// `seconds - 1`, …, 0, una vez por `second`. Cancelar la tarea la corta.
@MainActor
func countdown(_ seconds: Int, every second: Duration, onTick: @escaping @MainActor (Int) -> Void) -> Task<Void, Never> {
    onTick(seconds)
    return Task { @MainActor in
        var left = seconds
        while left > 0 {
            try? await Task.sleep(for: second)
            if Task.isCancelled { return }
            left -= 1
            onTick(left)
        }
    }
}
