package com.pluk.reader.account

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.GuestNotice
import com.pluk.reader.domain.account.GuestNoticePreference
import com.pluk.reader.domain.account.shouldShowGuestNotice
import com.pluk.reader.domain.flags.Setting
import com.pluk.reader.flags.FakeFeatureFlags
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

    private val flags = FakeFeatureFlags()
    private val book = LibraryBook("b", "Drácula", null, null, null)
    private val now = 1_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val week = 7 * day

    // GST-010, GST-026: sin sesión, con al menos un libro, salvo en los días siguientes a cerrarlo
    @Test
    fun rule() {
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = null, now = now, snoozeDays = 7))
        assertFalse(shouldShowGuestNotice(signedIn = true, hasBooks = true, dismissedAt = null, now = now, snoozeDays = 7))
        assertFalse(shouldShowGuestNotice(signedIn = false, hasBooks = false, dismissedAt = null, now = now, snoozeDays = 7))
        assertFalse(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = now - week + 1, now = now, snoozeDays = 7))
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = now - week, now = now, snoozeDays = 7))
        // GST-026: otro valor remoto; un negativo vale 0
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = now - 3 * day, now = now, snoozeDays = 3))
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissedAt = now, now = now, snoozeDays = -1))
    }

    // GST-010: aparece con el primer libro; la cruz lo esconde y a los 7 días vuelve
    @Test
    fun appearsWithTheFirstBookAndComesBackAfterSevenDays() = runTest {
        val books = Books(emptyList())
        val preference = FakeGuestNoticePreference()
        var clock = now
        assertFalse(GuestNotice(FakeSession(null), books, preference, flags) { clock }.visible.first())

        books.state.value = listOf(book)
        val notice = GuestNotice(FakeSession(null), books, preference, flags) { clock }
        assertTrue(notice.visible.first())

        notice.dismiss()
        assertEquals(now, preference.dismissedAt.value)
        assertFalse(notice.visible.first())

        clock = now + week
        assertTrue(GuestNotice(FakeSession(null), books, preference, flags) { clock }.visible.first())
    }

    // GST-010: con sesión no se muestra
    @Test
    fun notWithASession() = runTest {
        val notice = GuestNotice(FakeSession(AccountUser("u1", null)), Books(listOf(book)), FakeGuestNoticePreference(), flags) { now }
        assertFalse(notice.visible.first())
    }

    // GST-026: los días vienen de la configuración remota
    @Test
    fun snoozeDaysComeFromTheRemoteSetting() = runTest {
        val preference = FakeGuestNoticePreference(dismissedAt = now - 2 * day)
        val books = Books(listOf(book))
        assertFalse(GuestNotice(FakeSession(null), books, preference, flags) { now }.visible.first())

        flags.set(Setting.GuestNoticeSnoozeDays, 1)
        assertTrue(GuestNotice(FakeSession(null), books, preference, flags) { now }.visible.first())
    }
}
