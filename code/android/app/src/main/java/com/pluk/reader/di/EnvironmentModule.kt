package com.pluk.reader.di

import com.pluk.reader.BuildConfig
import com.pluk.reader.domain.environment.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Entorno del flavor del build (AND-008, ADR 0020). */
@Module
@InstallIn(SingletonComponent::class)
object EnvironmentModule {
    @Provides
    fun provideAppEnvironment(): AppEnvironment = AppEnvironment.fromName(BuildConfig.ENVIRONMENT)
}
