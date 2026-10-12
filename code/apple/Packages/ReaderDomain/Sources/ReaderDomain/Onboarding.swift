import Foundation

// Reglas del onboarding (ONB), las mismas que `Credentials.kt` y `Onboarding.kt` de Android.

/// Qué reglas de ONB-005 cumple una contraseña.
public struct PasswordCheck: Equatable, Sendable {
    public let longEnough: Bool
    public let hasNumber: Bool
    public let hasSymbol: Bool
    public let hasUppercase: Bool

    /// Reglas cumplidas, de 0 a 4: un tramo del medidor por cada una.
    public var score: Int { [longEnough, hasNumber, hasSymbol, hasUppercase].count { $0 } }

    /// ONB-005: al menos 8 caracteres y al menos dos de número, símbolo y mayúscula.
    public var isValid: Bool { longEnough && [hasNumber, hasSymbol, hasUppercase].count { $0 } >= 2 }

    public var strength: PasswordStrength { PasswordStrength.allCases[score] }
}

public enum PasswordStrength: CaseIterable, Sendable {
    case empty, weak, fair, good, strong
}

public let minPasswordLength = 8

public func checkPassword(_ password: String) -> PasswordCheck {
    PasswordCheck(
        longEnough: password.count >= minPasswordLength,
        hasNumber: password.contains { $0.isNumber },
        hasSymbol: password.contains { !$0.isLetter && !$0.isNumber && !$0.isWhitespace },
        hasUppercase: password.contains { $0.isUppercase }
    )
}

/// Formato mínimo: algo@algo.algo, sin espacios (ONB-004). La existencia la decide el servidor.
public func isValidEmail(_ email: String) -> Bool {
    let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.wholeMatch(of: /[^@\s]+@[^@\s]+\.[^@\s]+/) != nil
}

/// Primer nombre para saludar en "Ya estás adentro" (ONB-013).
public func firstName(_ displayName: String?) -> String? {
    displayName?.split(whereSeparator: \.isWhitespace).first.map(String.init)
}

/// Pantalla con la que abre la app (ONB-019).
public enum StartDestination: Equatable, Sendable {
    case welcome, verifyEmail, home
}

public func startDestination(_ user: AccountUser?) -> StartDestination {
    guard let user else { return .welcome }
    return user.needsEmailVerification ? .verifyEmail : .home
}

/// Géneros de O4, en el orden del diseño (ONB-009). El valor es el que guarda Android.
public enum Genre: String, CaseIterable, Sendable {
    case philosophy = "Philosophy"
    case classics = "Classics"
    case poetry = "Poetry"
    case sciFi = "SciFi"
    case history = "History"
    case essays = "Essays"
    case mystery = "Mystery"
    case romance = "Romance"
    case biography = "Biography"
    case horror = "Horror"
    case science = "Science"
    case travel = "Travel"
    case drama = "Drama"
}

public let minInterests = 3

public func canContinueInterests(_ selected: Set<Genre>) -> Bool {
    selected.count >= minInterests
}

/// Opciones de O5 (ONB-010).
public enum ReadingGoal: Int, CaseIterable, Sendable {
    case casual = 10
    case regular = 20
    case serious = 30
    case devoted = 60

    public var minutes: Int { rawValue }

    public static let `default` = ReadingGoal.regular
}

/// Recordatorio diario (ONB-010, ONB-012). Hora local del dispositivo.
public struct DailyReminder: Equatable, Sendable {
    public var enabled: Bool
    public let hour: Int
    public let minute: Int

    /// nil si la hora no existe.
    public init?(enabled: Bool, hour: Int, minute: Int) {
        guard (0...23).contains(hour), (0...59).contains(minute) else { return nil }
        self.enabled = enabled
        self.hour = hour
        self.minute = minute
    }

    public static let `default` = DailyReminder(enabled: true, hour: 21, minute: 30)!
}

/// Lo elegido en el onboarding. Vive en el dispositivo (ADR 0015).
public protocol OnboardingPreferences: Sendable {
    func interests() -> Set<Genre>
    func goal() -> ReadingGoal?
    func reminder() -> DailyReminder?
    func saveInterests(_ genres: Set<Genre>)
    func saveGoal(_ goal: ReadingGoal, reminder: DailyReminder)
}

/// Programa la notificación diaria (ONB-011, ONB-012). La implementación vive en la app.
public protocol ReminderScheduler: Sendable {
    /// Pide el permiso de notificaciones si todavía no se pidió. Devuelve si la app puede notificar.
    func requestPermission() async -> Bool
    /// Programa (o reprograma) la notificación diaria con la meta en el texto.
    func schedule(_ reminder: DailyReminder, goal: ReadingGoal?) async
    func cancel() async
}
