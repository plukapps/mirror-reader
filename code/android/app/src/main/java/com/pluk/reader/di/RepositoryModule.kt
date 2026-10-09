package com.pluk.reader.di

import com.pluk.reader.data.repository.BookRepositoryImpl
import com.pluk.reader.data.repository.LibraryRepositoryImpl
import com.pluk.reader.data.repository.PositionRepositoryImpl
import com.pluk.reader.data.repository.SettingsRepositoryImpl
import com.pluk.reader.data.repository.WelcomeRepositoryImpl
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import com.pluk.reader.domain.repository.WelcomeRepository
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
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPositionRepository(impl: PositionRepositoryImpl): PositionRepository

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindWelcomeRepository(impl: WelcomeRepositoryImpl): WelcomeRepository
}
