package com.pluk.reader

import android.app.Application
import android.util.Log
import com.pluk.reader.data.account.DevAccountSignIn
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ReaderApp : Application() {
    @Inject lateinit var devAccountSignIn: DevAccountSignIn

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Cuenta de desarrollo hasta que existan las pantallas de cuenta (K-052). Sin contraseñas en el log.
        appScope.launch { Log.i("Account", "Cuenta de desarrollo: ${devAccountSignIn.signInIfConfigured()}") }
    }
}
