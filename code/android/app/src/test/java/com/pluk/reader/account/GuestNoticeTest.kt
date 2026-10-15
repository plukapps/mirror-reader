package com.pluk.reader.account

import com.pluk.reader.domain.account.AccountUser
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** GST-010: aviso descartado en memoria. */
class FakeGuestNoticePreference(dismissed: Boolean = false) : GuestNoticePreference {
    override val dismissed = MutableStateFlow(dismissed)

    override suspend fun dismiss() {
        dismissed.value = true
    }
}

class GuestNoticeTest {
    private class Books(initial: List<LibraryBook>) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Rejected("x")
    }

    private val book = LibraryBook("b", "Drácula", null, null, null)

    // GST-010: sin sesión, con al menos un libro y sin descartar
    @Test
    fun rule() {
        assertTrue(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissed = false))
        assertFalse(shouldShowGuestNotice(signedIn = true, hasBooks = true, dismissed = false))
        assertFalse(shouldShowGuestNotice(signedIn = false, hasBooks = false, dismissed = false))
        assertFalse(shouldShowGuestNotice(signedIn = false, hasBooks = true, dismissed = true))
    }

    // GST-010: aparece con el primer libro y, al descartarlo, no vuelve
    @Test
    fun appearsWithTheFirstBookAndStaysDismissed() = runTest {
        val books = Books(emptyList())
        val notice = GuestNotice(FakeSession(null), books, FakeGuestNoticePreference())
        assertFalse(notice.visible.first())

        books.state.value = listOf(book)
        assertTrue(notice.visible.first())

        notice.dismiss()
        assertFalse(notice.visible.first())
    }

    // GST-010: con sesión no se muestra
    @Test
    fun notWithASession() = runTest {
        val notice = GuestNotice(FakeSession(AccountUser("u1", null)), Books(listOf(book)), FakeGuestNoticePreference())
        assertFalse(notice.visible.first())
    }
}
