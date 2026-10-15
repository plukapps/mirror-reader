package com.pluk.reader

import android.app.Application
import com.pluk.reader.domain.crash.CrashReportIdentity
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ReaderApp : Application() {
    @Inject lateinit var crashIdentity: CrashReportIdentity

    override fun onCreate() {
        super.onCreate()
        // CMP-010: los cierres llevan el uid de la cuenta, o nada sin sesión.
        crashIdentity.start()
    }
}
