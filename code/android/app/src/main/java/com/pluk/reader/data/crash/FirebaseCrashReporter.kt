package com.pluk.reader.data.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pluk.reader.domain.crash.CrashReporter
import javax.inject.Inject

/** Cierres inesperados en Firebase Crashlytics (CMP-010, ADR 0018). */
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    override fun setUserId(id: String) {
        FirebaseCrashlytics.getInstance().setUserId(id)
    }
}
