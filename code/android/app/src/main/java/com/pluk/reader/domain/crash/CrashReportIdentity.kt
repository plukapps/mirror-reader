package com.pluk.reader.domain.crash

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Dónde se registran los cierres inesperados (CMP-010, ADR 0018). */
fun interface CrashReporter {
    /** Identificador de la cuenta que acompaña a los próximos reportes; vacío para ninguna. */
    fun setUserId(id: String)
}

/**
 * CMP-010: los reportes de cierre llevan el `uid` de la cuenta (nunca el email ni el nombre) y, sin sesión (PRF-009),
 * ningún identificador. Sigue la sesión desde que arranca la app.
 */
@Singleton
class CrashReportIdentity @Inject constructor(
    private val account: AccountRepository,
    private val reporter: CrashReporter,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            account.user.map { it?.id.orEmpty() }.distinctUntilChanged().collect(reporter::setUserId)
        }
    }
}
