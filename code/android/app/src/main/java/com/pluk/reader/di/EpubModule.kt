package com.pluk.reader.di

import com.pluk.reader.ui.reader.NavigatorFragmentHost
import com.pluk.reader.ui.reader.NavigatorHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EpubModule {
    @Binds
    @Singleton
    abstract fun bindNavigatorHost(impl: NavigatorFragmentHost): NavigatorHost
}
