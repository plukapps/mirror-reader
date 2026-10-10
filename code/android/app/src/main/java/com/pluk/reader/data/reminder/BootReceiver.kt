package com.pluk.reader.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.onboarding.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Las alarmas no sobreviven al reinicio: se vuelve a programar el recordatorio (ONB-012). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var preferences: OnboardingPreferences

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = preferences.reminder.first()
                if (reminder?.enabled == true) scheduler.schedule(reminder, preferences.goal.first())
            } finally {
                pending.finish()
            }
        }
    }
}
