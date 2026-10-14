package com.pluk.reader.di

import com.pluk.reader.data.flags.FirebaseRemoteFlagSource
import com.pluk.reader.data.remote.FirebaseBookFileStore
import com.pluk.reader.domain.flags.FeatureFlags
import com.pluk.reader.domain.flags.RemoteFeatureFlags
import com.pluk.reader.domain.flags.RemoteFlagSource
import com.pluk.reader.data.remote.FirebaseRemovals
import com.pluk.reader.data.repository.BookEditorImpl
import com.pluk.reader.data.remote.FirestoreQuotaSource
import com.pluk.reader.data.remote.FirestorePositions
import com.pluk.reader.data.remote.FirestoreRemoteLibrary
import com.pluk.reader.data.repository.BookUploadRepositoryImpl
import com.pluk.reader.data.repository.CloudBooksRepositoryImpl
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.remote.RemotePositions
import com.pluk.reader.domain.remote.RemoteRemovals
import com.pluk.reader.domain.repository.BookRemovals
import com.pluk.reader.domain.usecase.CloudRemovals
import com.pluk.reader.domain.usecase.CloudListener
import com.pluk.reader.domain.usecase.CloudListeners
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.domain.usecase.LibrarySyncRequester
import com.pluk.reader.domain.remote.RemoteBookChanges
import com.pluk.reader.domain.repository.KnownBooks
import com.pluk.reader.domain.usecase.RemovalPushRequester
import com.pluk.reader.domain.usecase.RemovalSync
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.usecase.PositionFlusher
import com.pluk.reader.domain.usecase.PositionFlushRequester
import com.pluk.reader.domain.usecase.PositionSync
import com.pluk.reader.data.repository.WifiOnlyPreferenceImpl
import com.pluk.reader.data.sync.ConnectivityNetworkMonitor
import com.pluk.reader.domain.sync.NetworkMonitor
import com.pluk.reader.domain.sync.NetworkSyncGate
import com.pluk.reader.domain.sync.SyncGate
import com.pluk.reader.domain.sync.WifiOnlyPreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteModule {
    @Binds @Singleton abstract fun bindRemoteLibrary(impl: FirestoreRemoteLibrary): RemoteLibrary
    @Binds @Singleton abstract fun bindPositionFlusher(impl: PositionSync): PositionFlusher
    @Binds @Singleton abstract fun bindPositionFlushRequester(impl: PositionSync): PositionFlushRequester
    @Binds @Singleton abstract fun bindRemotePositions(impl: FirestorePositions): RemotePositions
    @Binds @Singleton abstract fun bindBookFileStore(impl: FirebaseBookFileStore): BookFileStore
    @Binds @Singleton abstract fun bindQuotaSource(impl: FirestoreQuotaSource): QuotaSource
    @Binds @Singleton abstract fun bindBookUploadRepository(impl: BookUploadRepositoryImpl): BookUploadRepository
    @Binds @Singleton abstract fun bindCloudBooksRepository(impl: CloudBooksRepositoryImpl): CloudBooksRepository

    // LIB-020: borrar un libro de la cuenta.
    @Binds @Singleton abstract fun bindRemoteRemovals(impl: FirebaseRemovals): RemoteRemovals
    @Binds @Singleton abstract fun bindBookRemovals(impl: BookEditorImpl): BookRemovals
    @Binds @Singleton abstract fun bindCloudRemovals(impl: RemovalSync): CloudRemovals
    @Binds @Singleton abstract fun bindRemovalPushRequester(impl: RemovalSync): RemovalPushRequester
    @Binds @Singleton abstract fun bindCloudListener(impl: CloudListeners): CloudListener

    // SYN-015: libros nuevos al instante.
    @Binds @Singleton abstract fun bindRemoteBookChanges(impl: FirestoreRemoteLibrary): RemoteBookChanges
    @Binds @Singleton abstract fun bindKnownBooks(impl: CloudBooksRepositoryImpl): KnownBooks
    @Binds @Singleton abstract fun bindLibrarySyncRequester(impl: LibrarySync): LibrarySyncRequester

    // ACC-007, SYN-017, ADR 0016: funciones que se prenden y apagan con Remote Config.
    @Binds @Singleton abstract fun bindFeatureFlags(impl: RemoteFeatureFlags): FeatureFlags
    @Binds @Singleton abstract fun bindRemoteFlagSource(impl: FirebaseRemoteFlagSource): RemoteFlagSource

    // PRF-023: sincronizar solo con Wi-Fi.
    @Binds @Singleton abstract fun bindSyncGate(impl: NetworkSyncGate): SyncGate
    @Binds @Singleton abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
    @Binds @Singleton abstract fun bindWifiOnlyPreference(impl: WifiOnlyPreferenceImpl): WifiOnlyPreference
}
