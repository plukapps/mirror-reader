import Foundation
import ReaderDomain

/// Intereses, meta y recordatorio del onboarding (ONB-009, ONB-010, ADR 0015), con las claves de Android.
final class UserDefaultsOnboardingPreferences: OnboardingPreferences, @unchecked Sendable {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func interests() -> Set<Genre> {
        Set((defaults.stringArray(forKey: Keys.interests) ?? []).compactMap(Genre.init(rawValue:)))
    }

    func goal() -> ReadingGoal? {
        (defaults.object(forKey: Keys.goalMinutes) as? Int).flatMap(ReadingGoal.init(rawValue:))
    }

    func reminder() -> DailyReminder? {
        guard
            let enabled = defaults.object(forKey: Keys.reminderEnabled) as? Bool,
            let hour = defaults.object(forKey: Keys.reminderHour) as? Int,
            let minute = defaults.object(forKey: Keys.reminderMinute) as? Int
        else { return nil }
        return DailyReminder(enabled: enabled, hour: hour, minute: minute)
    }

    func saveInterests(_ genres: Set<Genre>) {
        defaults.set(genres.map(\.rawValue).sorted(), forKey: Keys.interests)
    }

    func saveGoal(_ goal: ReadingGoal, reminder: DailyReminder) {
        defaults.set(goal.minutes, forKey: Keys.goalMinutes)
        defaults.set(reminder.enabled, forKey: Keys.reminderEnabled)
        defaults.set(reminder.hour, forKey: Keys.reminderHour)
        defaults.set(reminder.minute, forKey: Keys.reminderMinute)
    }

    private enum Keys {
        static let interests = "onboardingInterests"
        static let goalMinutes = "readingGoalMinutes"
        static let reminderEnabled = "reminderEnabled"
        static let reminderHour = "reminderHour"
        static let reminderMinute = "reminderMinute"
    }
}
