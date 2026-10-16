package com.pluk.reader.profile

import com.pluk.reader.domain.profile.StorageUnit
import com.pluk.reader.domain.profile.formatStorage
import com.pluk.reader.domain.profile.storageUnitFor
import org.junit.Assert.assertEquals
import org.junit.Test

// PRF-006: los tamaños usan la unidad del total, con un decimal como máximo y coma decimal.
class StorageFormatTest {
    private val mib = 1024L * 1024
    private val gib = 1024L * mib

    @Test
    fun totalOfOneGigabyteOrMoreUsesGigabytes() {
        assertEquals(StorageUnit.GB, storageUnitFor(2 * gib))
        assertEquals(StorageUnit.GB, storageUnitFor(gib))
    }

    @Test
    fun smallerTotalUsesMegabytes() {
        assertEquals(StorageUnit.MB, storageUnitFor(15 * mib))
    }

    @Test
    fun gigabytesWithOneDecimal() {
        assertEquals("1,2 GB", formatStorage(gib * 12 / 10, StorageUnit.GB))
        assertEquals("0,8 GB", formatStorage(gib * 8 / 10, StorageUnit.GB))
    }

    @Test
    fun wholeValuesHaveNoDecimal() {
        assertEquals("2 GB", formatStorage(2 * gib, StorageUnit.GB))
        assertEquals("15 MB", formatStorage(15 * mib, StorageUnit.MB))
    }

    @Test
    fun megabytesWithOneDecimal() {
        assertEquals("3,4 MB", formatStorage(mib * 34 / 10, StorageUnit.MB))
        assertEquals("0,2 MB", formatStorage(200 * 1024, StorageUnit.MB))
    }

    @Test
    fun zeroAndNegativeShowZero() {
        assertEquals("0 GB", formatStorage(0, StorageUnit.GB))
        assertEquals("0 MB", formatStorage(-5, StorageUnit.MB))
    }
}
