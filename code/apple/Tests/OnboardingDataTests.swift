import FirebaseAuth
import Foundation
import ReaderDomain
import Testing
import UserNotifications
@testable import Reader

// Datos del onboarding: preferencias en el dispositivo (ONB-009, ONB-010) y traducción de los errores de Firebase Auth
// (ONB-015, como `AuthErrorsTest` de Android). Ninguno toca Firebase.

struct OnboardingPreferencesTests {
    private let defaults: UserDefaults

    init() {
        let suite = "onboarding-tests-\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
    }

    @Test("ONB-010: sin elegir nada no hay meta ni recordatorio")
    func emptyByDefault() {
        let prefs = UserDefaultsOnboardingPreferences(defaults: defaults)
        #expect(prefs.goal() == nil)
        #expect(prefs.reminder() == nil)
    }

    @Test("ONB-010: lo elegido se guarda con las claves de Android")
    func savesAndReads() {
        let prefs = UserDefaultsOnboardingPreferences(defaults: defaults)
        prefs.saveGoal(.serious, reminder: DailyReminder(enabled: false, hour: 22, minute: 5)!)

        let reread = UserDefaultsOnboardingPreferences(defaults: defaults)
        #expect(reread.goal() == .serious)
        #expect(reread.reminder() == DailyReminder(enabled: false, hour: 22, minute: 5))
        #expect(defaults.integer(forKey: "readingGoalMinutes") == 30)
    }
}

struct AuthErrorMappingTests {
    private func error(_ code: AuthErrorCode) -> Error {
        NSError(domain: AuthErrors.domain, code: code.rawValue)
    }

    @Test("ONB-015: los errores del SDK se traducen sin revelar si el email existe")
    func mapsSdkErrors() {
        #expect(authError(error(.wrongPassword)) == .invalidCredentials)
        #expect(authError(error(.userNotFound)) == .invalidCredentials)
        #expect(authError(error(.invalidCredential)) == .invalidCredentials)
        #expect(authError(error(.emailAlreadyInUse)) == .emailInUse)
        #expect(authError(error(.userDisabled)) == .userDisabled)
        #expect(authError(error(.tooManyRequests)) == .tooManyRequests)
        #expect(authError(error(.networkError)) == .network)
        #expect(authError(error(.weakPassword)) == .weakPassword)
        #expect(authError(error(.expiredActionCode)) == .invalidLink)
        #expect(authError(error(.invalidActionCode)) == .invalidLink)
        #expect(authError(NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)) == .network)
        #expect(authError(NSError(domain: "otro", code: 1)) == .unknown)
    }
}

struct ReminderNotificationTests {
    @Test("ONB-012: una notificación por día a la hora elegida, con la meta en el texto")
    func dailyWithGoal() throws {
        let request = NotificationReminderScheduler.request(DailyReminder(enabled: true, hour: 22, minute: 5)!, goal: .serious)
        #expect(request.content.title == "Es hora de leer")
        #expect(request.content.body == "Tu meta de hoy: 30 minutos.")
        let trigger = try #require(request.trigger as? UNCalendarNotificationTrigger)
        #expect(trigger.repeats)
        #expect(trigger.dateComponents.hour == 22)
        #expect(trigger.dateComponents.minute == 5)
    }

    @Test("ONB-012: sin meta, un texto genérico")
    func withoutGoal() {
        let request = NotificationReminderScheduler.request(.default, goal: nil)
        #expect(request.content.body == "Un rato de lectura tranquila te espera.")
    }
}
