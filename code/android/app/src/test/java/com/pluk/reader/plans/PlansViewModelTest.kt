package com.pluk.reader.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.Billing
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.ui.plans.PlansUiState
import com.pluk.reader.ui.plans.PlansViewModel
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
class PlansViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val mib = 1024L * 1024

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.start(cache: FakeQuotaCache = FakeQuotaCache()): Pair<PlansViewModel, () -> PlansUiState> {
        val vm = PlansViewModel(cache)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm to { vm.uiState.value }
    }

    // PLN-004, PLN-005: arranca en mensual, con Lector elegido (en amarillo) y la tabla cerrada
    @Test
    fun startsMonthlyWithReaderSelected() = runTest(dispatcher) {
        val (_, state) = start()
        assertEquals(Billing.MONTHLY, state().billing)
        assertEquals(PlanId.PLUS, state().selected)
        assertFalse(state().tableOpen)
        assertEquals("$2.99", state().cta.price)
        assertEquals(listOf(false, true, false), PlanId.entries.map { state().isHighlighted(it) })
    }

    // PLN-004, PLN-006: el selector cambia la forma de pago y el botón
    @Test
    fun billingChangesTheCta() = runTest(dispatcher) {
        val (vm, state) = start()
        vm.onBilling(Billing.ANNUAL)
        advanceUntilIdle()
        assertEquals(Billing.ANNUAL, state().billing)
        assertEquals("$24.99", state().cta.price)
    }

    // PLN-005: Lector y Biblioteca se eligen; Gratis no
    @Test
    fun onlyPaidPlansCanBeSelected() = runTest(dispatcher) {
        val (vm, state) = start()
        vm.onSelect(PlanId.PRO)
        advanceUntilIdle()
        assertEquals(PlanId.PRO, state().selected)
        assertEquals(PlanId.PRO, state().cta.plan.id)
        // La tarjeta amarilla sigue a la elegida.
        assertEquals(listOf(false, false, true), PlanId.entries.map { state().isHighlighted(it) })
        vm.onSelect(PlanId.FREE)
        advanceUntilIdle()
        assertEquals(PlanId.PRO, state().selected)
    }

    // PLN-006: "Ver todo lo que incluye" despliega y repliega la tabla
    @Test
    fun tableToggles() = runTest(dispatcher) {
        val (vm, state) = start()
        vm.onToggleTable()
        advanceUntilIdle()
        assertTrue(state().tableOpen)
        vm.onToggleTable()
        advanceUntilIdle()
        assertFalse(state().tableOpen)
    }

    // PLN-003: el uso sale de la última cuota conocida, sin red; sin dato no hay fila
    @Test
    fun usageComesFromTheCache() = runTest(dispatcher) {
        val cache = FakeQuotaCache()
        val (_, state) = start(cache)
        assertNull(state().usage)
        assertEquals("15 MB", state().freeCloud)

        cache.quota.value = StorageQuota(12 * mib, 20 * mib)
        advanceUntilIdle()
        assertEquals("12 MB", state().usage?.used)
        assertEquals("20 MB", state().usage?.total)
        // PLN-001: Gratis muestra la cuota de la cuenta.
        assertEquals("20 MB", state().freeCloud)
    }

    // PLN-005: "Tu plan" en Gratis si es el plan de la cuenta
    @Test
    fun freeIsTheCurrentPlanOnlyForFreeAccounts() = runTest(dispatcher) {
        val cache = FakeQuotaCache(StorageQuota(0, 15 * mib))
        val (_, state) = start(cache)
        assertTrue(state().isOnFreePlan)
        cache.quota.value = StorageQuota(0, 15 * mib, plan = "plus")
        advanceUntilIdle()
        assertFalse(state().isOnFreePlan)
    }
}
