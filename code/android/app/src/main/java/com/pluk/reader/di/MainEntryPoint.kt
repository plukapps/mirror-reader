package com.pluk.reader.di

import com.pluk.reader.ui.reader.NavigatorFragmentHost
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Acceso a la fábrica de fragmentos antes de que Hilt inyecte `MainActivity`. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainEntryPoint {
    fun navigatorHost(): NavigatorFragmentHost
}
