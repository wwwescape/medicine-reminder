package com.wwwescape.medicinereminder.data.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.reminders.ReminderNotifier
import com.wwwescape.medicinereminder.data.reminders.ReminderReceiver
import com.wwwescape.medicinereminder.data.reminders.Reminders
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.time.LocalDate

/** Answers from the watch: the same as tapping Take all, Skip, Snooze or Close on the phone. */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearProtocol.PATH_ANSWER) return
        val json = runCatching { JSONObject(String(event.data, Charsets.UTF_8)) }.getOrNull() ?: return
        val action = json.optString(WearProtocol.KEY_ACTION)
        val app = applicationContext
        if (json.optBoolean(WearProtocol.KEY_TEST)) {
            // The Settings test logs nothing: any answer just ends it.
            ReminderNotifier.cancelTest(app)
            return
        }
        val (epochDay, minute) = WearProtocol.parseSlot(json.optString(WearProtocol.KEY_SLOT)) ?: return
        val date = LocalDate.ofEpochDay(epochDay)
        // This callback runs on a background thread, and the work is a few quick database calls.
        runBlocking {
            when (action) {
                WearProtocol.ACTION_TAKE -> Reminders.answerAll(app, date, minute, DoseStatus.TAKEN)
                WearProtocol.ACTION_SKIP -> Reminders.answerAll(app, date, minute, DoseStatus.SKIPPED)
                WearProtocol.ACTION_SNOOZE -> ReminderReceiver.snooze(app, date, minute)
                WearProtocol.ACTION_CLOSE -> ReminderNotifier.cancel(app, date, minute)
            }
        }
    }
}
