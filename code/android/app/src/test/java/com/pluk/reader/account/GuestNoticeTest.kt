package com.pluk.reader.account

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.GUEST_NOTICE_SNOOZE_MS
import com.pluk.reader.domain.account.GuestNotice
import com.pluk.reader.domain.account.GuestNoticePreference
import com.pluk.reader.domain.account.shouldShowGuestNotice
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.remote.FakeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GST-010: cuándo se cerró el aviso, en memoria. */
class FakeGuestNoticePreference(dismissedAt: Long? = null) : GuestNoticePreference {
    override val dismissedAt = MutableStateFlow(dismissedAt)

    override suspend fun dismiss(at: Long) {
        dismissedAt.value = at
    }
}

class GuestNoticeTest {
    private class Books(initial: List<LibraryBook>) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Rejected("x")
    }

    private val book = LibraryBook("b", "Drácula", null, null, null)
    private val now = 1_000_000_000L

    // GST-010: sin sesión, con al menos un libro, salvo en los 7 días siguientes a cerrarlo
    @Test
    fun rule() {
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = null, now = now))
        assertFalse(shouldShowGuestNotice(signedIn = true, hasBooks = true, dismissedAt = null, now = now))
        assertFalse(shouldShowGuestNotice(signedIn = false, hasBooks = false, dismissedAt = null, now = now))
        assertFalse(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = now - GUEST_NOTICE_SNOOZE_MS + 1, now = now))
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = now - GUEST_NOTICE_SNOOZE_MS, now = now))
    }

    // GST-010: aparece con el primer libro; la cruz lo esconde y a los 7 días vuelve
    @Test
    fun appearsWithTheFirstBookAndComesBackAfterSevenDays() = runTest {
        val books = Books(emptyList())
        val preference = FakeGuestNoticePreference()
        var clock = now
        assertFalse(GuestNotice(FakeSession(null), books, preference) { clock }.visible.first())

        books.state.value = listOf(book)
        val notice = GuestNotice(FakeSession(null), books, preference) { clock }
        assertTrue(notice.visible.first())

        notice.dismiss()
        assertEquals(now, preference.dismissedAt.value)
        assertFalse(notice.visible.first())

        clock = now + GUEST_NOTICE_SNOOZE_MS
        assertTrue(GuestNotice(FakeSession(null), books, preference) { clock }.visible.first())
    }

    // GST-010: con sesión no se muestra
    @Test
    fun notWithASession() = runTest {
        val notice = GuestNotice(FakeSession(AccountUser("u1", null)), Books(listOf(book)), FakeGuestNoticePreference()) { now }
        assertFalse(notice.visible.first())
    }
}
