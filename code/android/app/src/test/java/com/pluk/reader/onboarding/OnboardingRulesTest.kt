package com.pluk.reader.onboarding

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.AuthLink
import com.pluk.reader.domain.account.parseAuthLink
import com.pluk.reader.domain.onboarding.Genre
import com.pluk.reader.domain.onboarding.PasswordStrength
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.domain.onboarding.StartDestination
import com.pluk.reader.domain.onboarding.canContinueInterests
import com.pluk.reader.domain.onboarding.checkPassword
import com.pluk.reader.domain.onboarding.firstName
import com.pluk.reader.domain.onboarding.isValidEmail
import com.pluk.reader.domain.onboarding.nextReminderAt
import com.pluk.reader.domain.onboarding.startDestination
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas puras del onboarding: ONB-004, ONB-005, ONB-009, ONB-010, ONB-012, ONB-013, ONB-019. */
class OnboardingRulesTest {

    // ONB-005
    @Test
    fun passwordNeedsEightCharactersAndTwoOfNumberSymbolUppercase() {
        assertFalse(checkPassword("Ab1!").isValid) // corta
        assertFalse(checkPassword("abcdefgh").isValid) // ninguna extra
        assertFalse(checkPassword("abcdefg1").isValid) // solo número
        assertTrue(checkPassword("abcdefg1!").isValid) // número y símbolo
        assertTrue(checkPassword("Abcdefgh1").isValid) // mayúscula y número
        assertTrue(checkPassword("slow-reads-2026").isValid)
    }

    // ONB-005: un tramo del medidor por regla cumplida
    @Test
    fun strengthCountsTheRulesMet() {
        assertEquals(PasswordStrength.Empty, checkPassword("").strength)
        assertEquals(PasswordStrength.Weak, checkPassword("abcdefgh").strength)
        assertEquals(PasswordStrength.Fair, checkPassword("abcdefg1").strength)
        assertEquals(PasswordStrength.Good, checkPassword("slow-reads-2026").strength)
        assertEquals(PasswordStrength.Strong, checkPassword("Slow-reads-2026").strength)
        assertFalse(checkPassword("a b c d e").hasSymbol) // el espacio no cuenta como símbolo
    }

    // ONB-004
    @Test
    fun emailNeedsAMinimalFormat() {
        assertTrue(isValidEmail("ana.ruiz@mail.com"))
        assertTrue(isValidEmail("  ana@mail.co "))
        assertFalse(isValidEmail("ana"))
        assertFalse(isValidEmail("ana@mail"))
        assertFalse(isValidEmail("ana ruiz@mail.com"))
        assertFalse(isValidEmail(""))
    }

    // ONB-019
    @Test
    fun startDependsOnSessionAndVerification() {
        assertEquals(StartDestination.Welcome, startDestination(null))
        assertEquals(StartDestination.VerifyEmail, startDestination(AccountUser("u", "a@b.c", needsEmailVerification = true)))
        assertEquals(StartDestination.Home, startDestination(AccountUser("u", "a@b.c")))
    }

    // ONB-009
    @Test
    fun interestsNeedAtLeastThree() {
        assertFalse(canContinueInterests(setOf(Genre.Poetry, Genre.Drama)))
        assertTrue(canContinueInterests(setOf(Genre.Poetry, Genre.Drama, Genre.Essays)))
    }

    // ONB-010
    @Test
    fun goalOptionsMatchTheDesign() {
        assertEquals(listOf(10, 20, 30, 60), ReadingGoal.entries.map { it.minutes })
        assertEquals(ReadingGoal.Regular, ReadingGoal.Default)
    }

    // ONB-013
    @Test
    fun greetingUsesTheFirstName() {
        assertEquals("Ana", firstName("  Ana Ruiz "))
        assertNull(firstName("  "))
        assertNull(firstName(null))
    }

    // ONB-012: hoy si todavía no pasó la hora, si no mañana
    @Test
    fun nextReminderIsTheNextOccurrenceOfTheChosenTime() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val morning = ZonedDateTime.of(2026, 10, 10, 9, 0, 0, 0, zone)
        val night = ZonedDateTime.of(2026, 10, 10, 21, 30, 0, 0, zone)

        assertEquals(night.toInstant().toEpochMilli(), nextReminderAt(morning.toInstant().toEpochMilli(), zone, 21, 30))
        assertEquals(
            night.plusDays(1).toInstant().toEpochMilli(),
            nextReminderAt(night.toInstant().toEpochMilli(), zone, 21, 30),
        )
    }

    // ONB-007, ONB-018: enlaces de los emails abiertos en la app
    @Test
    fun authLinksAreRecognizedOnlyFromTheProjectHost() {
        val host = "demo.firebaseapp.com"
        assertEquals(
            AuthLink.ResetPassword("ab-C_1"),
            parseAuthLink("https://demo.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=ab-C_1&apiKey=k&lang=es", host),
        )
        assertEquals(AuthLink.VerifyEmail("x%y"), parseAuthLink("https://demo.firebaseapp.com/__/auth/action?oobCode=x%25y&mode=verifyEmail", host))
        assertNull(parseAuthLink("https://demo.firebaseapp.com/__/auth/action?mode=recoverEmail&oobCode=c", host))
        assertNull(parseAuthLink("https://demo.firebaseapp.com/__/auth/action?mode=resetPassword", host))
        assertNull(parseAuthLink("https://evil.example.com/__/auth/action?mode=resetPassword&oobCode=c", host))
        assertNull(parseAuthLink("http://demo.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=c", host))
        assertNull(parseAuthLink("content://media/book.epub", host))
    }
}
