package com.pluk.reader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.local.db.MIGRATION_1_2
import com.pluk.reader.data.local.db.MIGRATION_2_3
import com.pluk.reader.data.local.db.MIGRATION_3_4
import com.pluk.reader.data.local.db.MIGRATION_4_5
import com.pluk.reader.data.local.db.MIGRATION_5_6
import com.pluk.reader.data.local.db.MIGRATION_6_7
import com.pluk.reader.data.local.db.MIGRATION_7_8
import com.pluk.reader.data.local.db.MIGRATION_8_9
import com.pluk.reader.data.local.db.NoteDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()

    @Provides
    fun provideReadingPositionDao(db: ReaderDatabase): ReadingPositionDao = db.readingPositionDao()

    @Provides
    fun provideBookDao(db: ReaderDatabase): BookDao = db.bookDao()

    @Provides
    fun provideNoteDao(db: ReaderDatabase): NoteDao = db.noteDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("reader_settings") }
}
