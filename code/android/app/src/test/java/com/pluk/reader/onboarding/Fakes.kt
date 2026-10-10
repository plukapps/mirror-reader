package com.pluk.reader.onboarding

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthException
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.Genre
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.domain.onboarding.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow

fun authFailure(error: AuthError) = Result.failure<Nothing>(AuthException(error))

/** Cuenta falsa: guarda lo que se le pidió y responde lo que el test configura. */
class FakeAuth : AccountRepository, AuthRepository {
    override val user = MutableStateFlow<AccountUser?>(null)

    var createResult: Result<AccountUser> = Result.success(AccountUser("u1", "ana@mail.com", "Ana Ruiz", needsEmailVerification = true))
    var signInResult: Result<AccountUser> = Result.success(AccountUser("u1", "ana@mail.com"))
    var googleResult: Result<AccountUser> = Result.success(AccountUser("u1", "ana@gmail.com", "Ana"))
    var verified: Result<Boolean> = Result.success(false)
    var sendVerificationResult: Result<Unit> = Result.success(Unit)
    var discardResult: Result<Unit> = Result.success(Unit)
    var resetResult: Result<Unit> = Result.success(Unit)
    var resetEmail: Result<String> = Result.success("ana@mail.com")
    var confirmResult: Result<Unit> = Result.success(Unit)

    val created = mutableListOf<Triple<String, String, String>>()
    val signIns = mutableListOf<Pair<String, String>>()
    var verificationChecks = 0
    var verificationsSent = 0
    var discarded = 0
    val resetsSent = mutableListOf<String>()
    val confirmed = mutableListOf<Pair<String, String>>()

    override suspend fun signIn(email: String, password: String) = signInWithPassword(email, password)

    override suspend fun createAccount(name: String, email: String, password: String): Result<AccountUser> {
        created += Triple(name, email, password)
        return createResult
    }

    override suspend fun signInWithPassword(email: String, password: String): Result<AccountUser> {
        signIns += email to password
        return signInResult
    }

    override suspend fun signInWithGoogle(idToken: String) = googleResult

    override suspend fun sendEmailVerification(): Result<Unit> {
        verificationsSent++
        return sendVerificationResult
    }

    override suspend fun refreshEmailVerified(): Result<Boolean> {
        verificationChecks++
        return verified
    }

    override suspend fun discardUnverifiedAccount(): Result<Unit> {
        discarded++
        return discardResult
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        resetsSent += email
        return resetResult
    }

    override suspend fun emailForResetCode(code: String) = resetEmail

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> {
        confirmed += code to newPassword
        return confirmResult
    }
}

class FakePreferences : OnboardingPreferences {
    override val interests = MutableStateFlow<Set<Genre>>(emptySet())
    override val goal = MutableStateFlow<ReadingGoal?>(null)
    override val reminder = MutableStateFlow<DailyReminder?>(null)

    override suspend fun saveInterests(genres: Set<Genre>) {
        interests.value = genres
    }

    override suspend fun saveGoal(goal: ReadingGoal, reminder: DailyReminder) {
        this.goal.value = goal
        this.reminder.value = reminder
    }
}

class FakeScheduler(var notificationsAllowed: Boolean = true) : ReminderScheduler {
    var scheduled: Pair<DailyReminder, ReadingGoal?>? = null
    var cancelled = 0

    override fun schedule(reminder: DailyReminder, goal: ReadingGoal?) {
        scheduled = reminder to goal
    }

    override fun cancel() {
        scheduled = null
        cancelled++
    }

    override fun canNotify() = notificationsAllowed
}
