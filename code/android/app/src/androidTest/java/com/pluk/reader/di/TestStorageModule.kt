package com.pluk.reader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.local.db.ReadingPositionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.UUID
import javax.inject.Singleton

/** Cada prueba arranca con una base en memoria y un DataStore nuevo, sin tocar los datos reales. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [StorageModule::class])
object TestStorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReaderDatabase =
        Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()

    @Provides
    fun provideReadingPositionDao(db: ReaderDatabase): ReadingPositionDao = db.readingPositionDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
            File(context.cacheDir, "test-settings-${UUID.randomUUID()}.preferences_pb")
        }
}
