package com.pluk.reader.di

import com.pluk.reader.data.repository.BookEditorImpl
import com.pluk.reader.data.repository.BookRepositoryImpl
import com.pluk.reader.data.repository.LibraryRepositoryImpl
import com.pluk.reader.data.repository.NoteRepositoryImpl
import com.pluk.reader.data.repository.PositionRepositoryImpl
import com.pluk.reader.data.repository.QuotaCacheImpl
import com.pluk.reader.data.repository.AppearancePreferencesImpl
import com.pluk.reader.data.repository.SettingsRepositoryImpl
import com.pluk.reader.domain.appearance.AppearancePreferences
import com.pluk.reader.data.repository.SyncPreferencesImpl
import com.pluk.reader.data.repository.OnboardingPreferencesImpl
import com.pluk.reader.data.reminder.AlarmReminderScheduler
import com.pluk.reader.domain.onboarding.ReminderScheduler
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.repository.BookEditor
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.repository.NoteRepository
import com.pluk.reader.domain.repository.NoteSyncStore
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.PositionSyncStore
import com.pluk.reader.domain.repository.SettingsRepository
import com.pluk.reader.domain.repository.SyncPreferences
import com.pluk.reader.data.repository.UsageNoticePreferenceImpl
import com.pluk.reader.domain.plans.UsageNoticePreference
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindQuotaCache(impl: QuotaCacheImpl): QuotaCache

    @Binds
    @Singleton
    abstract fun bindUsageNoticePreference(impl: UsageNoticePreferenceImpl): UsageNoticePreference

    @Binds
    @Singleton
    abstract fun bindAppearancePreferences(impl: AppearancePreferencesImpl): AppearancePreferences

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPositionRepository(impl: PositionRepositoryImpl): PositionRepository

    @Binds
    @Singleton
    abstract fun bindPositionSyncStore(impl: PositionRepositoryImpl): PositionSyncStore

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindBookEditor(impl: BookEditorImpl): BookEditor

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindNoteSyncStore(impl: NoteRepositoryImpl): NoteSyncStore

    @Binds
    @Singleton
    abstract fun bindSyncPreferences(impl: SyncPreferencesImpl): SyncPreferences

    @Binds
    @Singleton
    abstract fun bindOnboardingPreferences(impl: OnboardingPreferencesImpl): OnboardingPreferences

    @Binds
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
