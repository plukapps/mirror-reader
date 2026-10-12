import Foundation
import Observation
import ReaderDomain

/// Intereses, O4 (ONB-009), como `InterestsViewModel` de Android.
@MainActor
@Observable
final class InterestsViewModel {
    private(set) var selected: Set<Genre> = []
    private(set) var done = false

    @ObservationIgnored private let preferences: OnboardingPreferences

    init(preferences: OnboardingPreferences) {
        self.preferences = preferences
    }

    var canContinue: Bool { canContinueInterests(selected) }

    func toggle(_ genre: Genre) {
        if selected.contains(genre) { selected.remove(genre) } else { selected.insert(genre) }
    }

    func continueOn() {
        guard canContinue else { return }
        preferences.saveInterests(selected)
        done = true
    }

    /// "Omitir": sigue sin guardar.
    func skip() { done = true }

    func onNavigated() { done = false }
}

/// Meta diaria y recordatorio, O5 (ONB-010, ONB-011), como `GoalViewModel` de Android.
@MainActor
@Observable
final class GoalViewModel {
    var goal: ReadingGoal = .default
    private(set) var reminder: DailyReminder = .default
    private(set) var busy = false
    /// ONB-011: se negó el permiso y el recordatorio quedó apagado. La vista lo avisa.
    private(set) var notificationsDenied = false
    private(set) var done = false

    @ObservationIgnored private let preferences: OnboardingPreferences
    @ObservationIgnored private let scheduler: ReminderScheduler

    init(preferences: OnboardingPreferences, scheduler: ReminderScheduler) {
        self.preferences = preferences
        self.scheduler = scheduler
    }

    var reminderEnabled: Bool {
        get { reminder.enabled }
        set { reminder.enabled = newValue }
    }

    func setReminderTime(hour: Int, minute: Int) {
        if let time = DailyReminder(enabled: reminder.enabled, hour: hour, minute: minute) { reminder = time }
    }

    /// "Terminar". Pide el permiso de notificaciones si hace falta; sin él la configuración se guarda igual, con el
    /// recordatorio apagado (ONB-011).
    func finish() async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        var chosen = reminder
        if chosen.enabled, await !scheduler.requestPermission() {
            chosen.enabled = false
            notificationsDenied = true
        }
        preferences.saveGoal(goal, reminder: chosen)
        if chosen.enabled {
            await scheduler.schedule(chosen, goal: goal)
        } else {
            await scheduler.cancel()
        }
        reminder = chosen
        done = true
    }

    /// "Omitir": sin meta ni recordatorio.
    func skip() { done = true }

    func onNavigated() { done = false }
}

/// "Ya estás adentro", O6 (ONB-013), como `AllSetViewModel` de Android.
@MainActor
@Observable
final class AllSetViewModel {
    private(set) var name: String?
    private(set) var goal: ReadingGoal?
    private(set) var reminder: DailyReminder?

    @ObservationIgnored private let account: AccountRepository
    @ObservationIgnored private let preferences: OnboardingPreferences

    init(account: AccountRepository, preferences: OnboardingPreferences) {
        self.account = account
        self.preferences = preferences
    }

    func load() async {
        name = firstName(await account.currentUser()?.displayName)
        goal = preferences.goal()
        reminder = preferences.reminder()
    }
}
