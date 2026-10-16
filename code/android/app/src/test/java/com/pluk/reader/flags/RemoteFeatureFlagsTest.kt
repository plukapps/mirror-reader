package com.pluk.reader.flags

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.RemoteFeatureFlags
import com.pluk.reader.domain.flags.RemoteFlagSource
import com.pluk.reader.domain.plans.DefaultPlans
import com.pluk.reader.domain.plans.Plan
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.profile.QuotaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteFeatureFlagsTest {
    private class FakeSource : RemoteFlagSource {
        /** Lo que tiene el servidor; se aplica al activar. */
        val server = mutableMapOf<String, Boolean>()
        private val applied = mutableMapOf<String, Boolean>()
        /** El catálogo de planes del servidor, ya leído (null: inválido o sin valor); se aplica al activar. */
        var serverPlans: List<Plan>? = DefaultPlans.all
        private var appliedPlans: List<Plan>? = DefaultPlans.all
        var plan: String? = null
        var offline = false
        var listener: (() -> Unit)? = null

        override suspend fun setPlan(plan: String) {
            this.plan = plan
        }

        override suspend fun activate() = Unit

        override suspend fun fetchAndActivate() {
            if (offline) error("sin conexión")
            applied.putAll(server)
            appliedNumbers.putAll(serverNumbers)
            appliedPlans = serverPlans
        }

        override fun value(key: String): Boolean? = applied[key]

        /** Números del servidor; se aplican al bajar. */
        val serverNumbers = mutableMapOf<String, Long>()
        private val appliedNumbers = mutableMapOf<String, Long>()

        override fun number(key: String): Long? = appliedNumbers[key]

        override fun plans(): List<Plan>? = appliedPlans

        override fun listen(onUpdate: () -> Unit): () -> Unit {
            listener = { applied.putAll(server); appliedPlans = serverPlans; onUpdate() }
            return { listener = null }
        }
    }

    private class FakeQuota(initial: StorageQuota?) : QuotaCache {
        override val quota = MutableStateFlow(initial)
        override suspend fun last() = quota.value
        override suspend fun save(quota: StorageQuota) {
            this.quota.value = quota
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val source = FakeSource()

    private fun flags(quota: StorageQuota? = null) = RemoteFeatureFlags(source, FakeQuota(quota), CoroutineScope(dispatcher))

    @After
    fun resetCatalog() = PlanCatalog.update(null)

    private val cheaperPlus = listOf(DefaultPlans.free, DefaultPlans.plus.copy(monthlyPrice = 1.99), DefaultPlans.pro)

    // ACC-007, K-290: al bajar la configuración, el catálogo de planes del servidor pasa a ser el vigente
    @Test
    fun publishesThePlanCatalog() = runTest(dispatcher) {
        source.serverPlans = cheaperPlus
        flags().startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1.99, PlanCatalog.of(PlanId.PLUS).monthlyPrice, 0.0)
    }

    // ACC-008: si el catálogo del servidor pasa a ser inválido, vuelven los valores por defecto (no el anterior)
    @Test
    fun anInvalidCatalogRestoresTheDefaults() = runTest(dispatcher) {
        source.serverPlans = cheaperPlus
        flags().startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(cheaperPlus, PlanCatalog.plans.value)
        source.serverPlans = null
        source.listener!!.invoke()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(DefaultPlans.all, PlanCatalog.plans.value)
    }

    // ACC-007: con la app a la vista, un cambio del catálogo llega sin esperar otra descarga
    @Test
    fun appliesCatalogChangesWhileListening() = runTest(dispatcher) {
        flags().startListening()
        dispatcher.scheduler.advanceUntilIdle()
        source.serverPlans = cheaperPlus
        source.listener!!.invoke()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(cheaperPlus, PlanCatalog.plans.value)
    }

    // SYN-017: si no se sabe todavía, vale prendida
    @Test
    fun notesSyncIsOnByDefaultAndOffline() = runTest(dispatcher) {
        source.offline = true
        val flags = flags()
        assertTrue(flags.isEnabled(Feature.NotesSync))
        flags.startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(flags.isEnabled(Feature.NotesSync))
    }

    // SYN-017, ACC-011: apagada en el servidor, se apaga al bajar la configuración
    @Test
    fun followsTheServerValue() = runTest(dispatcher) {
        source.server["notes_sync_enabled"] = false
        val flags = flags()
        flags.startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(flags.isEnabled(Feature.NotesSync))
    }

    // GST-026: los días del aviso sin cuenta valen 7 hasta que llega el valor del servidor
    @Test
    fun settingsStartWithTheDefaultAndFollowTheServer() = runTest(dispatcher) {
        source.serverNumbers["guest_notice_snooze_days"] = 3
        val flags = flags()
        assertEquals(7L, flags.setting(com.pluk.reader.domain.flags.Setting.GuestNoticeSnoozeDays).value)
        flags.startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3L, flags.setting(com.pluk.reader.domain.flags.Setting.GuestNoticeSnoozeDays).value)
    }

    // SYN-017: con la app a la vista, prender o apagar llega sin esperar otra descarga
    @Test
    fun appliesServerChangesWhileListening() = runTest(dispatcher) {
        val flags = flags()
        flags.startListening()
        dispatcher.scheduler.advanceUntilIdle()
        source.server["notes_sync_enabled"] = false
        source.listener!!.invoke()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(flags.state(Feature.NotesSync).value)
        source.server["notes_sync_enabled"] = true
        source.listener!!.invoke()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(flags.state(Feature.NotesSync).value)
        flags.stopListening()
        assertNull(source.listener)
    }

    // ACC-011: el plan de la cuenta viaja como señal, para las condiciones por plan; sin dato, Free
    @Test
    fun sendsTheAccountPlan() = runTest(dispatcher) {
        flags(StorageQuota(0, 1, plan = "plus")).startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("plus", source.plan)
        flags(null).startListening()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(StorageQuota.FREE_PLAN, source.plan)
    }
}
