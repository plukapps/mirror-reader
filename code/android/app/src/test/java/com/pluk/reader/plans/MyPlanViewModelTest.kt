package com.pluk.reader.plans

import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.DefaultPlans
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.ui.plans.MyPlanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** PLN-012: Mi plan muestra el último espacio conocido y lo refresca con el servidor. */
@OptIn(ExperimentalCoroutinesApi::class)
class MyPlanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val mib = 1024L * 1024

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSource(val result: Result<StorageQuota>) : QuotaSource {
        override suspend fun current() = result
    }

    @Test
    fun showsTheServerQuotaAndSavesIt() = runTest(dispatcher) {
        val cache = FakeQuotaCache(StorageQuota(1 * mib, 15 * mib))
        val vm = MyPlanViewModel(cache, FakeSource(Result.success(StorageQuota(12 * mib, 15 * mib))))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("12 MB", vm.uiState.value.usage?.used)
        assertEquals("15 MB", vm.uiState.value.usage?.total)
        assertEquals(StorageQuota(12 * mib, 15 * mib), cache.quota.value)
    }

    @Test
    fun offlineKeepsTheLastKnownQuota() = runTest(dispatcher) {
        val cache = FakeQuotaCache(StorageQuota(3 * mib, 15 * mib))
        val vm = MyPlanViewModel(cache, FakeSource(Result.failure(RemoteUnavailableException("x"))))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals("3 MB", vm.uiState.value.usage?.used)
    }

    @Test
    fun withoutAnyQuotaThereIsNoUsage() = runTest(dispatcher) {
        val vm = MyPlanViewModel(FakeQuotaCache(), FakeSource(Result.failure(RemoteUnavailableException("x"))))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNull(vm.uiState.value.usage)
        // PLN-014: sin dato, Free con su máximo por defecto
        assertEquals(PlanId.FREE, vm.uiState.value.plan)
        assertEquals(DefaultPlans.free.maxFileBytes, vm.uiState.value.maxFileBytes)
    }

    // PLN-017, ACC-010: el plan de la cuenta y el máximo por archivo que informa el servidor
    @Test
    fun showsThePlanOfTheAccountAndItsMaximum() = runTest(dispatcher) {
        val quota = StorageQuota(3 * mib, 50 * 1024 * mib, plan = "pro", maxFileBytes = 100 * mib)
        val vm = MyPlanViewModel(FakeQuotaCache(), FakeSource(Result.success(quota)))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(PlanId.PRO, vm.uiState.value.plan)
        assertEquals(100 * mib, vm.uiState.value.maxFileBytes)
    }

    // PLN-012: en Free también vale el máximo del servidor
    @Test
    fun theFreePlanUsesTheServerMaximum() = runTest(dispatcher) {
        val quota = StorageQuota(0, 15 * mib, maxFileBytes = 3 * mib)
        val vm = MyPlanViewModel(FakeQuotaCache(), FakeSource(Result.success(quota)))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(PlanId.FREE, vm.uiState.value.plan)
        assertEquals(3 * mib, vm.uiState.value.maxFileBytes)
    }
}
