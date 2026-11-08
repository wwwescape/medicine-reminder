package com.wwwescape.medicinereminder.data.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.schedule.ScheduledDose
import com.wwwescape.medicinereminder.data.wear.WearBridge
import com.wwwescape.medicinereminder.data.wear.WearProtocol
import com.wwwescape.medicinereminder.ui.icons.MedicineArt
import com.wwwescape.medicinereminder.ui.reminder.ReminderActivity
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatMinute
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDate
import java.time.LocalTime

/** The ringing notification for a dose slot. It keeps sounding until it's answered or closed,
 * and opens [ReminderActivity] full screen when the device allows it. */
object ReminderNotifier {

    /** A channel's sound can't be changed once created, so a new sound needs a new channel. */
    private const val CHANNEL_ID = "dose_reminders_alarm"
    /** "dose_reminders" used the system alarm sound; "dose_reminders_shake" addressed the bundled
     * sound by resource id, which can point elsewhere after an update. */
    private val RETIRED_CHANNELS = listOf("dose_reminders", "dose_reminders_shake")

    /** Pixabay attribution for the bundled reminder sound (res/raw/shake_meds.mp3). Names
     * are proper nouns and the license asks for this wording, so it isn't translated. */
    const val SOUND_CREDIT_AUTHOR = "freesound_community"
    const val SOUND_CREDIT_AUTHOR_URL =
        "https://pixabay.com/users/freesound_community-46691455/?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=87054"
    const val SOUND_CREDIT_SOURCE = "Pixabay"
    const val SOUND_CREDIT_SOURCE_URL =
        "https://pixabay.com/sound-effects//?utm_source=link-attribution&utm_medium=referral&utm_campaign=music&utm_content=87054"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        RETIRED_CHANNELS.forEach(manager::deleteNotificationChannel)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
            // Shaking pills, repeated until the dose is answered (the notification is
            // FLAG_INSISTENT), on the alarm volume. Addressed by name: the channel stores this
            // URI, and a numeric resource id can change between app versions.
            setSound(
                Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/raw/shake_meds"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun show(context: Context, date: LocalDate, minute: Int, doses: List<ScheduledDose>, fullScreen: Boolean) {
        if (!canNotify(context) || doses.isEmpty()) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            notificationId(date, minute),
            ReminderActivity.intent(context, date, minute),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = ringing(context, doses, minute, open, fullScreen)
            .addAction(0, context.getString(R.string.action_take_all), slotAction(context, ReminderReceiver.ACTION_TAKE_ALL, date, minute))
            .addAction(0, context.getString(R.string.action_snooze), slotAction(context, ReminderReceiver.ACTION_SNOOZE, date, minute))
        post(context, notificationId(date, minute), builder)
        WearBridge.showReminder(context, WearProtocol.slotId(date.toEpochDay(), minute), minute, doses, test = false)
    }

    /** The same ringing reminder for a sample medicine, from Settings: sound, full screen and
     * all, with nothing logged. Its one action stops it. */
    fun showTest(context: Context, fullScreen: Boolean) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            ReminderActivity.testIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            TEST_NOTIFICATION_ID,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_TEST_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val now = LocalTime.now()
        val sample = TestReminder.sampleDose(context)
        val builder = ringing(context, listOf(sample), now.hour * 60 + now.minute, open, fullScreen)
            .addAction(0, context.getString(R.string.action_stop), stop)
        post(context, TEST_NOTIFICATION_ID, builder)
        WearBridge.showReminder(context, WearProtocol.TEST_SLOT, now.hour * 60 + now.minute, listOf(sample), test = true)
    }

    fun cancelTest(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(TEST_NOTIFICATION_ID)
        WearBridge.dismissReminder(context, WearProtocol.TEST_SLOT)
        closedSlots.tryEmit(WearProtocol.TEST_SLOT)
    }

    /** Below every slot id (minutes since the epoch) and the visit ids. */
    private const val TEST_NOTIFICATION_ID = 1

    /** A reminder that rings until it's answered: alarm category, shown on the lock screen, and
     * FLAG_INSISTENT so the channel's sound repeats for as long as the notification is up. */
    private fun ringing(
        context: Context,
        doses: List<ScheduledDose>,
        minute: Int,
        open: PendingIntent,
        fullScreen: Boolean,
    ): NotificationCompat.Builder {
        val first = doses.first().medicine
        val title = if (doses.size == 1) {
            context.getString(R.string.notification_title_one, first.name)
        } else {
            context.getString(R.string.notification_title_many, doses.size)
        }
        val lines = doses.map { dose ->
            listOfNotNull(
                dose.medicine.name.takeIf { doses.size > 1 },
                doseText(context, dose.medicine),
                dose.medicine.instructions.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prescriptions)
            .setLargeIcon(MedicineArt.bitmap(first.shape, first.color1, first.color2, 192))
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setSubText(formatMinute(context, minute))
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            // The companion watch app shows its own full-screen reminder; don't also mirror this.
            .setLocalOnly(true)
        if (fullScreen) builder.setFullScreenIntent(open, true)
        return builder
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        val notification = builder.build().apply { flags = flags or Notification.FLAG_INSISTENT }
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    /** Stops a slot ringing everywhere: the notification, the watch, and an open full-screen
     * reminder (which watches [closedSlots]). */
    fun cancel(context: Context, date: LocalDate, minute: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(date, minute))
        val slot = WearProtocol.slotId(date.toEpochDay(), minute)
        WearBridge.dismissReminder(context, slot)
        closedSlots.tryEmit(slot)
    }

    /** Slots that stopped ringing, by [WearProtocol.slotId] — for instance answered on the watch. */
    val closedSlots = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private fun slotAction(context: Context, action: String, date: LocalDate, minute: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Reminders.slotIntent(context, action, date, minute),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Stable per slot, so a follow-up replaces the slot's earlier notification. */
    fun notificationId(date: LocalDate, minute: Int): Int = ((date.toEpochDay() * 1440 + minute) % Int.MAX_VALUE).toInt()
}
