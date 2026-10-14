package com.pluk.reader.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.shouldShowUsageNotice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLN-011: aviso suave al 80 % sin llenar, una sola vez. */
class UsageNoticeTest {
    private val mib = 1024L * 1024

    @Test
    fun showsFromEightyPercent() {
        assertTrue(shouldShowUsageNotice(StorageQuota(12 * mib, 15 * mib), dismissed = false))
        assertTrue(shouldShowUsageNotice(StorageQuota(15 * mib - 1, 15 * mib), dismissed = false))
    }

    @Test
    fun notBelowEightyPercent() {
        assertFalse(shouldShowUsageNotice(StorageQuota(12 * mib - 1, 15 * mib), dismissed = false))
    }

    @Test
    fun notWhenFull() {
        assertFalse(shouldShowUsageNotice(StorageQuota(15 * mib, 15 * mib), dismissed = false))
        assertFalse(shouldShowUsageNotice(StorageQuota(16 * mib, 15 * mib), dismissed = false))
    }

    @Test
    fun notOnceDismissedOrWithoutQuota() {
        assertFalse(shouldShowUsageNotice(StorageQuota(12 * mib, 15 * mib), dismissed = true))
        assertFalse(shouldShowUsageNotice(null, dismissed = false))
        assertFalse(shouldShowUsageNotice(StorageQuota(0, 0), dismissed = false))
    }
}
