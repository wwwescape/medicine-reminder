package com.wwwescape.medicinereminder.data.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wwwescape.medicinereminder.MainActivity
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.data.db.MedicineDatabase
import com.wwwescape.medicinereminder.util.formatAppointmentWhen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val Appointment.dateTime: LocalDateTime get() = date.atTime(LocalTime.of(minute / 60, minute % 60))

/**
 * Reminders ahead of doctor's visits. Like dose reminders, exactly one alarm is kept: the next
 * reminder due across all visits. Every reminder due at that same moment is shown when it fires.
 * These are ordinary notifications, not ringing alarms.
 */
object AppointmentReminders {

    private const val CHANNEL_ID = "appointments"
    private const val ALARM_REQUEST = 1
    internal const val EXTRA_AT = "at"

    /** Each (visit, minutes-before) pair with its moment, for every visit still ahead. */
    private fun reminderTimes(appointments: List<Appointment>): List<Pair<Appointment, LocalDateTime>> =
        appointments.flatMap { appointment -> appointment.reminders.map { appointment to appointment.dateTime.minusMinutes(it.toLong()) } }

    suspend fun reschedule(context: Context) {
        val now = LocalDateTime.now()
        val next = reminderTimes(MedicineDatabase.get(context).appointments().getAll())
            .map { it.second }
            .filter { it > now }
            .minOrNull()
        val pending = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            Intent(context, AppointmentReceiver::class.java).apply { if (next != null) putExtra(EXTRA_AT, next.toMillis()) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        when {
            next == null -> alarmManager.cancel(pending)
            Reminders.canScheduleExact(context) -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toMillis(), pending)
            else -> alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toMillis(), pending)
        }
    }

    internal suspend fun fire(context: Context, atMillis: Long) {
        val due = reminderTimes(MedicineDatabase.get(context).appointments().getAll())
            .filter { it.second.toMillis() == atMillis }
            .map { it.first }
            .distinctBy { it.id }
        due.forEach { show(context, it) }
        reschedule(context)
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_appointments), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notification_channel_appointments_description)
            },
        )
    }

    private fun show(context: Context, appointment: Appointment) {
        if (!ReminderNotifier.canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = listOf(formatAppointmentWhen(context, appointment), appointment.doctorName, appointment.place)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prescriptions)
            .setContentTitle(appointment.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(text, appointment.notes).filter { it.isNotBlank() }.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_BASE + appointment.id.toInt(), notification)
    }

    /** Clear of the dose-slot ids, which are minutes since the epoch (in the tens of millions). */
    private const val NOTIFICATION_ID_BASE = 1_000_000_000

    private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class AppointmentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getLongExtra(AppointmentReminders.EXTRA_AT, 0L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppointmentReminders.fire(context.applicationContext, at)
            } finally {
                pending.finish()
            }
        }
    }
}
