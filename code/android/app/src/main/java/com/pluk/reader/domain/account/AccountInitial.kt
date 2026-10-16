package com.pluk.reader.domain.account

/** PRF-005, HOM-012: la inicial del nombre o, si no hay, del email, en mayúscula. Vacía si no hay ninguno. */
fun AccountUser.initial(): String = (displayName ?: email)?.trim()?.firstOrNull()?.uppercase() ?: ""
