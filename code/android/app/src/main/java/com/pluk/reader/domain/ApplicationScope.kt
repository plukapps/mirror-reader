package com.pluk.reader.domain

import javax.inject.Qualifier

/** Scope de corrutinas para trabajo que debe sobrevivir a las pantallas, como la sincronización de la biblioteca. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
