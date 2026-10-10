package com.pluk.reader.di

import com.pluk.reader.data.store.EpubDownloader
import com.pluk.reader.data.store.OpenLibraryCatalog
import com.pluk.reader.data.store.StoreAcquisitionsImpl
import com.pluk.reader.domain.store.StoreAcquisitions
import com.pluk.reader.domain.store.StoreCatalog
import com.pluk.reader.domain.store.StoreDelivery
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Tienda, prueba de concepto (STO-001 a STO-008). */
@Module
@InstallIn(SingletonComponent::class)
abstract class StoreModule {
    @Binds
    abstract fun bindStoreCatalog(impl: OpenLibraryCatalog): StoreCatalog

    @Binds
    abstract fun bindStoreDelivery(impl: EpubDownloader): StoreDelivery

    @Binds
    abstract fun bindStoreAcquisitions(impl: StoreAcquisitionsImpl): StoreAcquisitions
}
