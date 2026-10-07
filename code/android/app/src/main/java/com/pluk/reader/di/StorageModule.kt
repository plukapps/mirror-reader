package com.pluk.reader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.local.db.MIGRATION_1_2
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.local.db.ReadingPositionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReaderDatabase =
        Room.databaseBuilder(context, ReaderDatabase::class.java, "reader.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideReadingPositionDao(db: ReaderDatabase): ReadingPositionDao = db.readingPositionDao()

    @Provides
    fun provideBookDao(db: ReaderDatabase): BookDao = db.bookDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("reader_settings") }
}
