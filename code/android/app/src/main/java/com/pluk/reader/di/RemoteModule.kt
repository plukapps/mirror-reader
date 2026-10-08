package com.pluk.reader.di

import com.pluk.reader.data.remote.FirebaseBookFileStore
import com.pluk.reader.data.remote.FirestoreQuotaSource
import com.pluk.reader.data.remote.FirestoreRemoteLibrary
import com.pluk.reader.data.repository.BookUploadRepositoryImpl
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.repository.BookUploadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {
    @Binds @Singleton abstract fun bindRemoteLibrary(impl: FirestoreRemoteLibrary): RemoteLibrary
    @Binds @Singleton abstract fun bindBookFileStore(impl: FirebaseBookFileStore): BookFileStore
    @Binds @Singleton abstract fun bindQuotaSource(impl: FirestoreQuotaSource): QuotaSource
    @Binds @Singleton abstract fun bindBookUploadRepository(impl: BookUploadRepositoryImpl): BookUploadRepository
}
