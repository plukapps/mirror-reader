package com.pluk.reader.profile

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.SignInMethod
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.SyncPreferences
import com.pluk.reader.onboarding.FakeAuth
import com.pluk.reader.ui.profile.ProfileUiState
import com.pluk.reader.ui.profile.ProfileViewModel
import com.pluk.reader.ui.profile.StorageUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val gib = 1024L * 1024 * 1024

    private class FakeQuota(val result: Result<StorageQuota>) : QuotaSource {
        override suspend fun current(): Result<StorageQuota> = result
    }

    private class FakeCache(var saved: StorageQuota? = null) : QuotaCache {
        override suspend fun last(): StorageQuota? = saved
        override suspend fun save(quota: StorageQuota) {
            saved = quota
        }
    }

    private class FakeDevice : SyncPreferences {
        override suspend fun deviceId() = "d1"
        override fun deviceName() = "Pixel 8"
        override suspend fun lastPositionsSeenAt(): Long? = null
        override suspend fun setLastPositionsSeenAt(value: Long) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.state(
        user: AccountUser?,
        quota: FakeQuota = FakeQuota(Result.success(StorageQuota(gib * 12 / 10, 2 * gib))),
        cache: FakeCache = FakeCache(),
    ): ProfileUiState {
        val auth = FakeAuth().apply { this.user.value = user }
        val vm = ProfileViewModel(auth, quota, cache, FakeDevice())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm.uiState.value
    }

    // PRF-005: nombre, email e inicial del nombre
    @Test
    fun headerShowsNameEmailAndInitial() = runTest(dispatcher) {
        val state = state(AccountUser("u1", "ana.ruiz@gmail.com", "ana Ruiz"))
        assertEquals("ana Ruiz", state.name)
        assertEquals("ana.ruiz@gmail.com", state.email)
        assertEquals("A", state.initial)
    }

    // PRF-005: sin nombre, la inicial sale del email y el email va solo
    @Test
    fun headerWithoutNameUsesEmail() = runTest(dispatcher) {
        val state = state(AccountUser("u1", "luis@mail.com", displayName = null))
        assertNull(state.name)
        assertEquals("luis@mail.com", state.email)
        assertEquals("L", state.initial)
    }

    // PRF-008: "Cambiar contraseña" solo para cuentas de email y contraseña
    @Test
    fun changePasswordOnlyForPasswordAccounts() = runTest(dispatcher) {
        assertTrue(state(AccountUser("u1", "a@mail.com", signInMethod = SignInMethod.PASSWORD)).canChangePassword)
        assertFalse(state(AccountUser("u1", "a@gmail.com", signInMethod = SignInMethod.GOOGLE)).canChangePassword)
        assertFalse(state(AccountUser("u1", "a@icloud.com", signInMethod = SignInMethod.OTHER)).canChangePassword)
    }

    // PRF-006: espacio usado, total, libre y proporción, en la unidad del total
    @Test
    fun storageInUnitOfTotal() = runTest(dispatcher) {
        val state = state(AccountUser("u1", "a@mail.com"))
        assertEquals(StorageUi(used = "1,2 GB", total = "2 GB", free = "0,8 GB", fraction = 0.6f, isFreePlan = true), state.storage)
        assertFalse(state.storageFailed)
    }

    // PRF-006: la proporción no pasa de 1 aunque el uso supere la cuota
    @Test
    fun fractionIsCappedAtOne() = runTest(dispatcher) {
        val quota = FakeQuota(Result.success(StorageQuota(3 * gib, 2 * gib)))
        val state = state(AccountUser("u1", "a@mail.com"), quota)
        assertEquals(1f, state.storage?.fraction)
        assertEquals("0 GB", state.storage?.free)
    }

    // PRF-006: "Mejorar plan" solo en el plan gratuito
    @Test
    fun paidPlanIsNotFree() = runTest(dispatcher) {
        val quota = FakeQuota(Result.success(StorageQuota(gib, 2 * gib, plan = "plus")))
        assertEquals(false, state(AccountUser("u1", "a@mail.com"), quota).storage?.isFreePlan)
    }

    // PRF-006: el último valor leído se guarda para la próxima vez
    @Test
    fun savesReadQuota() = runTest(dispatcher) {
        val cache = FakeCache()
        state(AccountUser("u1", "a@mail.com"), cache = cache)
        assertEquals(StorageQuota(gib * 12 / 10, 2 * gib), cache.saved)
    }

    // PRF-006: sin conexión se muestra el último valor conocido
    @Test
    fun failureShowsCachedQuota() = runTest(dispatcher) {
        val quota = FakeQuota(Result.failure(RemoteUnavailableException("sin red")))
        val cache = FakeCache(StorageQuota(gib / 2, 2 * gib))
        val state = state(AccountUser("u1", "a@mail.com"), quota, cache)
        assertEquals("0,5 GB", state.storage?.used)
        assertFalse(state.storageFailed)
    }

    // PRF-006: si nunca se leyó y falla, se avisa
    @Test
    fun failureWithoutCacheIsReported() = runTest(dispatcher) {
        val quota = FakeQuota(Result.failure(RemoteUnavailableException("sin red")))
        val state = state(AccountUser("u1", "a@mail.com"), quota)
        assertNull(state.storage)
        assertTrue(state.storageFailed)
    }

    // PRF-006: mientras llega el valor nuevo, se ve el guardado
    @Test
    fun cachedQuotaShowsWhileLoading() = runTest(dispatcher) {
        val quota = FakeQuota(Result.success(StorageQuota(gib, 2 * gib)))
        val pending = CompletableDeferred<Unit>()
        val slow = object : QuotaSource {
            override suspend fun current(): Result<StorageQuota> {
                pending.await()
                return quota.result
            }
        }
        val auth = FakeAuth().apply { user.value = AccountUser("u1", "a@mail.com") }
        val vm = ProfileViewModel(auth, slow, FakeCache(StorageQuota(gib / 2, 2 * gib)), FakeDevice())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("0,5 GB", vm.uiState.value.storage?.used)
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals("1 GB", vm.uiState.value.storage?.used)
    }

    // PRF-007: este dispositivo con su nombre
    @Test
    fun showsThisDeviceName() = runTest(dispatcher) {
        assertEquals("Pixel 8", state(AccountUser("u1", "a@mail.com")).deviceName)
    }
}
