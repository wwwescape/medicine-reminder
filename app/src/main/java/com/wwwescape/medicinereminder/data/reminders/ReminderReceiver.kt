package com.wwwescape.medicinereminder.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.settings.MAX_FOLLOW_UPS
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Alarms (a dose is due, a follow-up or snooze ended) and the notification's own buttons. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // The Settings test isn't tied to a dose slot.
        when (intent.action) {
            ACTION_TEST -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        ReminderNotifier.showTest(app, SettingsRepository.current(app).reminders.fullScreen)
                    } finally {
                        pending.finish()
                    }
                }
                return
            }
            ACTION_TEST_STOP -> {
                ReminderNotifier.cancelTest(app)
                return
            }
        }
        val date = LocalDate.ofEpochDay(intent.getLongExtra(Reminders.EXTRA_DATE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return)
        val minute = intent.getIntExtra(Reminders.EXTRA_MINUTE, -1).takeIf { it >= 0 } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                handle(app, intent.action, date, minute, intent.getIntExtra(Reminders.EXTRA_ATTEMPT, 0))
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String?, date: LocalDate, minute: Int, attempt: Int) {
        val settings = SettingsRepository.current(context).reminders
        when (action) {
            ACTION_DUE, ACTION_FOLLOW_UP -> {
                val due = Reminders.unanswered(context, date, minute)
                if (due.isNotEmpty()) {
                    ReminderNotifier.show(context, date, minute, due, settings.fullScreen)
                    val followUp = settings.followUp.minutes
                    if (followUp != null && attempt < MAX_FOLLOW_UPS) {
                        Reminders.scheduleFollowUp(context, date, minute, attempt + 1, followUp)
                    }
                }
                if (action == ACTION_DUE) Reminders.reschedule(context)
            }
            ACTION_TAKE_ALL -> Reminders.answerAll(context, date, minute, DoseStatus.TAKEN)
            ACTION_SNOOZE -> snooze(context, date, minute)
        }
    }

    companion object {
        const val ACTION_DUE = "com.wwwescape.medicinereminder.DUE"
        const val ACTION_FOLLOW_UP = "com.wwwescape.medicinereminder.FOLLOW_UP"
        const val ACTION_TAKE_ALL = "com.wwwescape.medicinereminder.TAKE_ALL"
        const val ACTION_SNOOZE = "com.wwwescape.medicinereminder.SNOOZE"
        const val ACTION_TEST = "com.wwwescape.medicinereminder.TEST"
        const val ACTION_TEST_STOP = "com.wwwescape.medicinereminder.TEST_STOP"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Silences the slot now and rings again after the snooze length. A snooze restarts the
         * follow-up count. */
        suspend fun snooze(context: Context, date: LocalDate, minute: Int) {
            val snooze = SettingsRepository.current(context).reminders.snooze
            ReminderNotifier.cancel(context, date, minute)
            Reminders.scheduleFollowUp(context, date, minute, 0, snooze.minutes)
        }
    }
}

/** Alarms don't survive a reboot, an app update or a clock change, so set the next one again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reminders.reschedule(context.applicationContext)
                AppointmentReminders.reschedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
