package com.pluk.reader.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.Billing
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.plans.ctaFor
import com.pluk.reader.domain.profile.storageUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCatalogTest {
    private val mib = 1024L * 1024
    private val gib = 1024L * mib

    // PLN-001: tres planes, en orden, con el espacio y los precios provisionales
    @Test
    fun catalogHasThreePlans() {
        assertEquals(listOf(PlanId.FREE, PlanId.READER, PlanId.LIBRARY), PlanCatalog.all.map { it.id })
        assertEquals(5 * gib, PlanCatalog.reader.cloudBytes)
        assertEquals(50 * gib, PlanCatalog.library.cloudBytes)
        assertEquals("$2.99", PlanCatalog.reader.price(Billing.MONTHLY))
        assertEquals("$24.99", PlanCatalog.reader.price(Billing.ANNUAL))
        assertEquals("$2.08", PlanCatalog.reader.annualPerMonth)
        assertEquals("$5.99", PlanCatalog.library.price(Billing.MONTHLY))
        assertEquals("$49.99", PlanCatalog.library.price(Billing.ANNUAL))
        assertEquals("$4.17", PlanCatalog.library.annualPerMonth)
    }

    // PLN-001: Gratis usa la cuota real de la cuenta; sin dato, la de hoy (15 MB)
    @Test
    fun freeCloudSpaceIsTheAccountQuota() {
        assertEquals(20 * mib, PlanCatalog.cloudBytes(PlanCatalog.free, StorageQuota(0, 20 * mib)))
        assertEquals(15 * mib, PlanCatalog.cloudBytes(PlanCatalog.free, null))
        assertEquals(5 * gib, PlanCatalog.cloudBytes(PlanCatalog.reader, StorageQuota(0, 20 * mib)))
    }

    // PLN-006: sin prueba gratis; el botón lleva el precio del período elegido
    @Test
    fun ctaUsesThePriceOfTheBilling() {
        val annual = ctaFor(PlanId.READER, Billing.ANNUAL)
        assertEquals("$24.99", annual.price)
        assertEquals(Billing.ANNUAL, annual.billing)

        val monthly = ctaFor(PlanId.LIBRARY, Billing.MONTHLY)
        assertEquals("$5.99", monthly.price)
        assertEquals(PlanId.LIBRARY, monthly.plan.id)
    }

    // PLN-001, PLN-014: tamaño máximo por archivo de cada plan
    @Test
    fun maxFileSizePerPlan() {
        assertEquals(2 * mib, PlanCatalog.free.maxFileBytes)
        assertEquals(50 * mib, PlanCatalog.reader.maxFileBytes)
        assertEquals(100 * mib, PlanCatalog.library.maxFileBytes)
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
