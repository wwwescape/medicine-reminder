package com.wwwescape.medicinereminder.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.wwwescape.medicinereminder.MainActivity
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.MedicineDatabase
import com.wwwescape.medicinereminder.data.schedule.Schedule
import com.wwwescape.medicinereminder.data.schedule.ScheduledDose
import com.wwwescape.medicinereminder.data.schedule.key
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps exactly one "next dose" alarm set (the earliest unanswered dose with reminders on), plus
 * one follow-up alarm per time slot that was snoozed or went unanswered. A slot is a date and
 * minute; all doses due at the same moment share one reminder.
 */
object Reminders {

    const val EXTRA_DATE = "date"
    const val EXTRA_MINUTE = "minute"
    const val EXTRA_ATTEMPT = "attempt"

    private const val NEXT_ALARM_REQUEST = 0
    private const val TEST_ALARM_REQUEST = 2

    suspend fun reschedule(context: Context) {
        val now = LocalDateTime.now()
        val next = nextReminder(context, now)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            NEXT_ALARM_REQUEST,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DUE).apply {
                if (next != null) {
                    putExtra(EXTRA_DATE, next.toLocalDate().toEpochDay())
                    putExtra(EXTRA_MINUTE, next.hour * 60 + next.minute)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (next == null) {
            alarmManager.cancel(pending)
        } else {
            setAlarm(context, next.toMillis(), pending)
        }
    }

    private suspend fun nextReminder(context: Context, after: LocalDateTime): LocalDateTime? {
        val db = MedicineDatabase.get(context)
        val courses = db.courses().getAll().associateBy { it.id }
        val medicines = db.medicines().getAll()
        val today = after.toLocalDate()
        // Only today's and later answers matter: a dose logged early (taken ahead of its time)
        // must not ring. Looking a day back covers a slot just after midnight.
        val logged = db.doseLog().getBetween(today.minusDays(1), today.plusDays(400)).map { it.key() }.toSet()
        return Schedule.nextReminder(after, medicines, courses, logged)
    }

    /** The doses due at [date]/[minute] that are still unanswered. */
    suspend fun unanswered(context: Context, date: LocalDate, minute: Int): List<ScheduledDose> {
        val db = MedicineDatabase.get(context)
        val courses = db.courses().getAll().associateBy { it.id }
        val medicines = db.medicines().getAll()
        val logs = db.doseLog().getBetween(date, date).associateBy { it.key() }
        return Schedule.unansweredAt(date, minute, medicines, courses, logs)
    }

    /** Answers every dose still open in a slot the same way (Take all, or an answer from the
     * watch), which also stops the slot ringing. */
    suspend fun answerAll(context: Context, date: LocalDate, minute: Int, status: DoseStatus) {
        unanswered(context, date, minute).forEach {
            MedicineRepository.logDose(context, it.key, status, it.medicine.doseAmount)
        }
        ReminderNotifier.cancel(context, date, minute)
    }

    /** After a dose in a slot was answered: once nothing in the slot is left, stop ringing and
     * drop its follow-up. Always moves the next alarm on. */
    suspend fun onSlotChanged(context: Context, date: LocalDate, minute: Int) {
        if (unanswered(context, date, minute).isEmpty()) {
            ReminderNotifier.cancel(context, date, minute)
            cancelFollowUp(context, date, minute)
        }
        reschedule(context)
    }

    fun scheduleFollowUp(context: Context, date: LocalDate, minute: Int, attempt: Int, delayMinutes: Int) {
        val at = System.currentTimeMillis() + delayMinutes * 60_000L
        setAlarm(context, at, followUpIntent(context, date, minute, attempt))
    }

    fun cancelFollowUp(context: Context, date: LocalDate, minute: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(followUpIntent(context, date, minute, 0))
    }

    /** One PendingIntent per slot (told apart by its data URI), so a new follow-up replaces the
     * slot's previous one. */
    private fun followUpIntent(context: Context, date: LocalDate, minute: Int, attempt: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            slotIntent(context, ReminderReceiver.ACTION_FOLLOW_UP, date, minute).putExtra(EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun slotIntent(context: Context, action: String, date: LocalDate, minute: Int): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(slotUri(date, minute))
            .putExtra(EXTRA_DATE, date.toEpochDay())
            .putExtra(EXTRA_MINUTE, minute)

    fun slotUri(date: LocalDate, minute: Int): Uri = Uri.parse("medicinereminder://slot/${date.toEpochDay()}/$minute")

    /** Rings the Settings test reminder after [delaySeconds], through a real alarm, so it can
     * be seen as it would appear with the phone locked. */
    fun scheduleTest(context: Context, delaySeconds: Int) {
        val pending = PendingIntent.getBroadcast(
            context,
            TEST_ALARM_REQUEST,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_TEST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(context, System.currentTimeMillis() + delaySeconds * 1000L, pending)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** An alarm-clock alarm when allowed — exact, and honored in Doze — otherwise the best
     * inexact alarm that still fires while idle. */
    private fun setAlarm(context: Context, atMillis: Long, operation: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) {
            val show = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
