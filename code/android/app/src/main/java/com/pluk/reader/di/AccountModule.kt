package com.pluk.reader.di

import com.pluk.reader.data.account.FirebaseAccountRepository
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountModule {
    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: FirebaseAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAccountRepository): AuthRepository
}
