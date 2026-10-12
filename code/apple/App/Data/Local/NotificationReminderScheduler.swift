import Foundation
import ReaderDomain
import UserNotifications

/// Recordatorio diario con una notificación local que se repite (ONB-011, ONB-012, ADR 0015). El sistema la conserva al
/// reiniciar el dispositivo; tocarla abre la app.
struct NotificationReminderScheduler: ReminderScheduler {
    static let identifier = "daily-reading-reminder"

    func requestPermission() async -> Bool {
        let center = UNUserNotificationCenter.current()
        switch await center.notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: return true
        case .denied: return false
        default: return (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        }
    }

    func schedule(_ reminder: DailyReminder, goal: ReadingGoal?) async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [Self.identifier])
        try? await center.add(Self.request(reminder, goal: goal))
    }

    func cancel() async {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [Self.identifier])
    }

    /// La notificación de cada día: "Es hora de leer" y la meta (ONB-012), con los textos de Android.
    static func request(_ reminder: DailyReminder, goal: ReadingGoal?) -> UNNotificationRequest {
        let content = UNMutableNotificationContent()
        content.title = "Es hora de leer"
        content.body = goal.map { "Tu meta de hoy: \($0.minutes) minutos." } ?? "Un rato de lectura tranquila te espera."
        content.sound = .default
        let time = DateComponents(hour: reminder.hour, minute: reminder.minute)
        let trigger = UNCalendarNotificationTrigger(dateMatching: time, repeats: true)
        return UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
    }
}
