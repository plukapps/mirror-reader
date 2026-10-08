package com.pluk.reader.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Instancias del SDK de Firebase (ADR 0005, ADR 0007). Solo la capa `data` las usa. */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // Room es la fuente de verdad (ADR 0002): el caché en disco de Firestore se reemplaza por
    // uno en memoria para no tener dos cachés que se desincronicen. Los ajustes deben fijarse
    // antes del primer uso de la instancia.
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings { }) }
    }

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}
