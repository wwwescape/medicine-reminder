package com.wwwescape.medicinereminder.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** Messages from the phone app: a reminder started or stopped ringing. */
class WatchListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val text = String(event.data, Charsets.UTF_8)
        when (event.path) {
            WearProtocol.PATH_SHOW -> WatchReminders.show(applicationContext, text, event.sourceNodeId)
            WearProtocol.PATH_DISMISS -> {
                val slot = runCatching { JSONObject(text).optString(WearProtocol.KEY_SLOT) }.getOrNull() ?: return
                WatchReminders.dismiss(applicationContext, slot)
            }
        }
    }
}
