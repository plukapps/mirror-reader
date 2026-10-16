import Foundation
import Testing
@testable import ReaderDomain

struct GuestNoticeTests {
    private let now = Date(timeIntervalSince1970: 1_000_000)
    private let day: TimeInterval = 86_400

    @Test("GST-010, GST-026: sin sesión y con libros, salvo en los días siguientes a cerrarlo; un negativo vale 0")
    func rule() {
        #expect(shouldShowGuestNotice(signedIn: false, hasBooks: true, dismissedAt: nil, now: now, snoozeDays: 7))
        #expect(!shouldShowGuestNotice(signedIn: true, hasBooks: true, dismissedAt: nil, now: now, snoozeDays: 7))
        #expect(!shouldShowGuestNotice(signedIn: false, hasBooks: false, dismissedAt: nil, now: now, snoozeDays: 7))
        #expect(!shouldShowGuestNotice(signedIn: false, hasBooks: true, dismissedAt: now.addingTimeInterval(-7 * day + 1), now: now, snoozeDays: 7))
        #expect(shouldShowGuestNotice(signedIn: false, hasBooks: true, dismissedAt: now.addingTimeInterval(-7 * day), now: now, snoozeDays: 7))
        #expect(shouldShowGuestNotice(signedIn: false, hasBooks: true, dismissedAt: now.addingTimeInterval(-3 * day), now: now, snoozeDays: 3))
        #expect(shouldShowGuestNotice(signedIn: false, hasBooks: true, dismissedAt: now, now: now, snoozeDays: -1))
    }
}
