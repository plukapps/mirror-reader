package com.pluk.reader.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.Billing
import com.pluk.reader.domain.plans.DefaultPlans
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.plans.ctaFor
import com.pluk.reader.domain.profile.storageUsage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCatalogTest {
    private val mib = 1024L * 1024
    private val gib = 1024L * mib

    @After
    fun resetCatalog() = PlanCatalog.update(null)

    // PLN-001: precio fijo con "$" y dos decimales; sin costo, "$0"
    @Test
    fun pricesAreFixedText() {
        assertEquals("$0", DefaultPlans.free.price(Billing.MONTHLY))
        assertEquals("$0", DefaultPlans.free.annualPerMonth)
        val plan = DefaultPlans.plus.copy(monthlyPrice = 3.0, annualPrice = 30.0)
        assertEquals("$3.00", plan.price(Billing.MONTHLY))
        assertEquals("$2.50", plan.annualPerMonth)
    }

    // ACC-007, ACC-008: el catálogo vigente reemplaza a los por defecto; inválido (null), vuelven los por defecto
    @Test
    fun theCurrentCatalogReplacesTheDefaults() {
        val plus = DefaultPlans.plus.copy(cloudBytes = 7 * gib, maxFileBytes = 7 * mib, monthlyPrice = 3.49)
        PlanCatalog.update(listOf(DefaultPlans.free, plus, DefaultPlans.pro))
        assertEquals(plus, PlanCatalog.of(PlanId.PLUS))
        assertEquals(plus, PlanCatalog.plans.value[1])
        assertEquals("$3.49", ctaFor(PlanId.PLUS, Billing.MONTHLY).price)
        // ACC-008: sin el máximo del servidor, el del plan en el catálogo vigente
        assertEquals(7 * mib, PlanCatalog.maxFileBytes(StorageQuota(0, 1, plan = "plus")))
        PlanCatalog.update(null)
        assertEquals(DefaultPlans.plus, PlanCatalog.of(PlanId.PLUS))
    }

    // PLN-001: el espacio de Free sin cuota conocida (o en una cuenta paga) sale del catálogo vigente
    @Test
    fun freeSpaceComesFromTheCurrentCatalog() {
        PlanCatalog.update(listOf(DefaultPlans.free.copy(cloudBytes = 20 * mib), DefaultPlans.plus, DefaultPlans.pro))
        assertEquals(20 * mib, PlanCatalog.cloudBytes(PlanCatalog.of(PlanId.FREE), null))
        assertEquals(20 * mib, PlanCatalog.cloudBytes(PlanCatalog.of(PlanId.FREE), StorageQuota(0, 50 * gib, plan = "pro")))
        assertEquals(12 * mib, PlanCatalog.cloudBytes(PlanCatalog.of(PlanId.FREE), StorageQuota(0, 12 * mib)))
    }

    // PLN-001: tres planes, en orden, con el espacio y los precios provisionales
    @Test
    fun catalogHasThreePlans() {
        assertEquals(listOf(PlanId.FREE, PlanId.PLUS, PlanId.PRO), DefaultPlans.all.map { it.id })
        assertEquals(5 * gib, DefaultPlans.plus.cloudBytes)
        assertEquals(50 * gib, DefaultPlans.pro.cloudBytes)
        assertEquals("$2.99", DefaultPlans.plus.price(Billing.MONTHLY))
        assertEquals("$24.99", DefaultPlans.plus.price(Billing.ANNUAL))
        assertEquals("$2.08", DefaultPlans.plus.annualPerMonth)
        assertEquals("$5.99", DefaultPlans.pro.price(Billing.MONTHLY))
        assertEquals("$49.99", DefaultPlans.pro.price(Billing.ANNUAL))
        assertEquals("$4.17", DefaultPlans.pro.annualPerMonth)
    }

    // PLN-001: Free usa la cuota real de la cuenta; sin dato, la de hoy (15 MB)
    @Test
    fun freeCloudSpaceIsTheAccountQuota() {
        assertEquals(20 * mib, PlanCatalog.cloudBytes(DefaultPlans.free, StorageQuota(0, 20 * mib)))
        assertEquals(15 * mib, PlanCatalog.cloudBytes(DefaultPlans.free, null))
        assertEquals(5 * gib, PlanCatalog.cloudBytes(DefaultPlans.plus, StorageQuota(0, 20 * mib)))
    }

    // PLN-001 (K-291): en una cuenta paga, Free no muestra la cuota de la cuenta sino la de Free (15 MB)
    @Test
    fun freeCloudSpaceOfAPaidAccountIsTheFreeQuota() {
        assertEquals(15 * mib, PlanCatalog.cloudBytes(DefaultPlans.free, StorageQuota(0, 50 * gib, plan = "pro")))
        assertEquals(15 * mib, PlanCatalog.cloudBytes(DefaultPlans.free, StorageQuota(0, 5 * gib, plan = "plus")))
    }

    // PLN-006: sin prueba gratis; el botón lleva el precio del período elegido
    @Test
    fun ctaUsesThePriceOfTheBilling() {
        val annual = ctaFor(PlanId.PLUS, Billing.ANNUAL)
        assertEquals("$24.99", annual.price)
        assertEquals(Billing.ANNUAL, annual.billing)

        val monthly = ctaFor(PlanId.PRO, Billing.MONTHLY)
        assertEquals("$5.99", monthly.price)
        assertEquals(PlanId.PRO, monthly.plan.id)
    }

    // PLN-014, ACC-007: el plan sale de la cuota con los ids del backend (`plus` y `pro`); desconocido o sin dato, Free
    @Test
    fun planOfQuota() {
        assertEquals(PlanId.FREE, PlanCatalog.planOf(null))
        assertEquals(PlanId.FREE, PlanCatalog.planOf(StorageQuota(0, 1)))
        assertEquals(PlanId.PLUS, PlanCatalog.planOf(StorageQuota(0, 1, plan = "plus")))
        assertEquals(PlanId.PRO, PlanCatalog.planOf(StorageQuota(0, 1, plan = "pro")))
        assertEquals(PlanId.FREE, PlanCatalog.planOf(StorageQuota(0, 1, plan = "reader")))
        assertEquals(PlanId.FREE, PlanCatalog.planOf(StorageQuota(0, 1, plan = "library")))
    }

    // ACC-008, ACC-010: el máximo por archivo que vale es el del servidor; sin dato, el fijo del plan
    @Test
    fun maxFileBytesComesFromTheServer() {
        assertEquals(2 * mib, PlanCatalog.maxFileBytes(null))
        assertEquals(2 * mib, PlanCatalog.maxFileBytes(StorageQuota(0, 1)))
        assertEquals(50 * mib, PlanCatalog.maxFileBytes(StorageQuota(0, 1, plan = "plus")))
        assertEquals(7 * mib, PlanCatalog.maxFileBytes(StorageQuota(0, 1, maxFileBytes = 7 * mib)))
        assertEquals(7 * mib, PlanCatalog.maxFileBytes(StorageQuota(0, 1, plan = "plus", maxFileBytes = 7 * mib)))
    }

    // PLN-001, PLN-014: tamaño máximo por archivo de cada plan
    @Test
    fun maxFileSizePerPlan() {
        assertEquals(2 * mib, DefaultPlans.free.maxFileBytes)
        assertEquals(50 * mib, DefaultPlans.plus.maxFileBytes)
        assertEquals(100 * mib, DefaultPlans.pro.maxFileBytes)
    }

    // PLN-003: "Usás X de Y" con la unidad y el formato de PRF-006, y la proporción
    @Test
    fun usageUsesProfileFormat() {
        val usage = storageUsage(StorageQuota(12 * mib, 15 * mib))
        assertEquals("12 MB", usage.used)
        assertEquals("15 MB", usage.total)
        assertEquals("3 MB", usage.free)
        assertEquals(80, usage.percent)
        assertEquals(0.8f, usage.fraction, 0.001f)
    }

    @Test
    fun usageIsClampedWhenOverQuota() {
        val usage = storageUsage(StorageQuota(3 * gib, 2 * gib))
        assertEquals(1f, usage.fraction)
        assertEquals(100, usage.percent)
        assertEquals("0 GB", usage.free)
    }

    @Test
    fun usageWithZeroQuotaIsEmpty() {
        val usage = storageUsage(StorageQuota(0, 0))
        assertEquals(0f, usage.fraction)
        assertEquals(0, usage.percent)
    }
}
