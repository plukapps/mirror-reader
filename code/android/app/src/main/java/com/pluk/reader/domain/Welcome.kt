package com.pluk.reader.domain

/** WEL-003: la bienvenida se muestra si nunca se completó o si no hay sesión. */
fun showsWelcome(completed: Boolean, signedIn: Boolean): Boolean = !completed || !signedIn
