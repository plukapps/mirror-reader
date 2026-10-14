package com.pluk.reader.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.repository.QuotaCacheImpl
import com.pluk.reader.domain.account.StorageQuota
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** PRF-006, PLN-009, ACC-010: la última cuota leída se recuerda, con el máximo por archivo del servidor. */
@RunWith(AndroidJUnit4::class)
class QuotaCacheTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.cacheDir, "quota-${UUID.randomUUID()}.preferences_pb")
    private val cache = QuotaCacheImpl(PreferenceDataStoreFactory.create(scope = scope) { file })

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun remembersTheServerMaximumPerFile() = runBlocking {
        assertNull(cache.last())
        val quota = StorageQuota(3, 15 * MIB, plan = "pro", maxFileBytes = 100 * MIB)
        cache.save(quota)
        assertEquals(quota, cache.last())
    }

    // Una cuota sin máximo (cuenta anterior al catálogo) no deja el de una lectura vieja
    @Test
    fun aQuotaWithoutMaximumClearsTheOldOne() = runBlocking {
        cache.save(StorageQuota(0, 15 * MIB, maxFileBytes = 2 * MIB))
        cache.save(StorageQuota(0, 15 * MIB))
        assertNull(cache.last()?.maxFileBytes)
    }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
