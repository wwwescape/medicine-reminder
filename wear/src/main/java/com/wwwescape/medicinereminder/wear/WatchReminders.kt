package com.wwwescape.medicinereminder.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File

/** One medicine in a ringing reminder, as the phone described it. */
class WatchDose(val name: String, val dose: String, val instructions: String, val icon: Bitmap?)

/** A reminder ringing on the phone, mirrored here. [phoneNode] is where answers go back to. */
class WatchReminder(
    val slot: String,
    val test: Boolean,
    val time: String,
    val doses: List<WatchDose>,
    val phoneNode: String,
)

/**
 * The reminder the phone last asked this watch to ring, and the notification that opens it full
 * screen. The phone decides everything; the watch only shows the reminder and passes the answer
 * back.
 */
object WatchReminders {

    private const val TAG = "WatchReminders"
    /** "dose_reminders" was silent, so a reminder that only peeked in (the watch in use)
     * could go unnoticed; a channel's vibration can't be changed, hence a new id. */
    private const val CHANNEL_ID = "dose_reminders_buzz"
    private const val RETIRED_CHANNEL_ID = "dose_reminders"
    private const val NOTIFICATION_ID = 1
    private const val FILE_NAME = "active-reminder.json"
    private const val KEY_NODE = "node"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Slots the phone says stopped ringing (answered, snoozed or closed there). */
    val dismissed = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** A new reminder arrived while one may already be on screen. */
    val shown = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** Called with the phone's message: keep it (a file survives this process being stopped
     * before the reminder opens) and ring. */
    fun show(context: Context, json: String, phoneNode: String) {
        val payload = runCatching { JSONObject(json) }.getOrNull() ?: return
        payload.put(KEY_NODE, phoneNode)
        val reminder = parse(payload) ?: return
        File(context.filesDir, FILE_NAME).writeText(payload.toString())
        notify(context, reminder)
        shown.tryEmit(Unit)
    }

    fun dismiss(context: Context, slot: String) {
        val active = current(context)
        if (active == null || active.slot == slot) clear(context)
        dismissed.tryEmit(slot)
    }

    /** The reminder to show, if one is ringing. */
    fun current(context: Context): WatchReminder? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { parse(JSONObject(file.readText())) }.getOrNull()
    }

    /** Sends the answer to the phone, which logs it and stops ringing everywhere. */
    fun answer(context: Context, reminder: WatchReminder, action: String) {
        clear(context)
        val app = context.applicationContext
        val message = JSONObject()
            .put(WearProtocol.KEY_SLOT, reminder.slot)
            .put(WearProtocol.KEY_TEST, reminder.test)
            .put(WearProtocol.KEY_ACTION, action)
            .toString()
            .toByteArray(Charsets.UTF_8)
        scope.launch {
            try {
                Wearable.getMessageClient(app).sendMessage(reminder.phoneNode, WearProtocol.PATH_ANSWER, message).await()
            } catch (e: Exception) {
                Log.w(TAG, "Answer not sent to the phone: ${e.message}")
            }
        }
    }

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun parse(json: JSONObject): WatchReminder? {
        val array = json.optJSONArray(WearProtocol.KEY_DOSES) ?: return null
        val doses = (0 until array.length()).map { i ->
            val dose = array.getJSONObject(i)
            WatchDose(
                name = dose.optString(WearProtocol.KEY_NAME),
                dose = dose.optString(WearProtocol.KEY_DOSE),
                instructions = dose.optString(WearProtocol.KEY_INSTRUCTIONS),
                icon = dose.optString(WearProtocol.KEY_ICON).takeIf { it.isNotEmpty() }?.let(::decode),
            )
        }
        if (doses.isEmpty()) return null
        return WatchReminder(
            slot = json.optString(WearProtocol.KEY_SLOT),
            test = json.optBoolean(WearProtocol.KEY_TEST),
            time = json.optString(WearProtocol.KEY_TIME),
            doses = doses,
            phoneNode = json.optString(KEY_NODE),
        )
    }

    private fun decode(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    /** The notification opens [ReminderActivity] full screen (which buzzes) and stays as
     * a way back to it; on its own it only buzzes. */
    private fun notify(context: Context, reminder: WatchReminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(RETIRED_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_reminders_description)
                    // The full-screen reminder buzzes until answered. If the watch only shows a
                    // heads-up instead (it's in use), this buzz says to tap it.
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            ReminderActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val first = reminder.doses.first()
        val title = if (reminder.doses.size == 1) {
            context.getString(R.string.notification_title_one, first.name)
        } else {
            context.resources.getQuantityString(R.plurals.notification_title_many, reminder.doses.size, reminder.doses.size)
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prescriptions)
            .setContentTitle(title)
            .setContentText(reminder.doses.joinToString(", ") { it.dose })
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Can't post the reminder: ${it.message}") }
    }
}
