package com.pluk.reader.di

import com.pluk.reader.BuildConfig
import com.pluk.reader.data.account.DevCredentials
import com.pluk.reader.data.account.FirebaseAccountRepository
import com.pluk.reader.domain.account.AccountRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {
    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: FirebaseAccountRepository): AccountRepository

    companion object {
        // Vacías en release y cuando falta local.properties (ver app/build.gradle.kts).
        @Provides
        fun provideDevCredentials() = DevCredentials(BuildConfig.DEV_ACCOUNT_EMAIL, BuildConfig.DEV_ACCOUNT_PASSWORD)
    }
}
