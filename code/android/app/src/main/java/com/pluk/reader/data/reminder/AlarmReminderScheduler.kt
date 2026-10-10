package com.pluk.reader.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.domain.onboarding.ReminderScheduler
import com.pluk.reader.domain.onboarding.nextReminderAt
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject

/**
 * Recordatorio diario con una alarma inexacta que se repite cada día (ONB-012, ADR 0014). No pide el permiso de
 * alarmas exactas: Android puede correrla un rato, que alcanza para un recordatorio.
 */
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: DailyReminder, goal: ReadingGoal?) {
        val first = nextReminderAt(System.currentTimeMillis(), ZoneId.systemDefault(), reminder.hour, reminder.minute)
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY, pendingIntent(goal))
    }

    override fun cancel() {
        alarms.cancel(pendingIntent(goal = null))
    }

    override fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    // Mismo request code y clase: reprogramar reemplaza la alarma anterior y cancelar la encuentra.
    private fun pendingIntent(goal: ReadingGoal?): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_GOAL_MINUTES, goal?.minutes ?: 0),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val REQUEST_CODE = 2130
    }
}
