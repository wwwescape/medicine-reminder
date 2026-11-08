package com.wwwescape.medicinereminder.data.wear

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import com.wwwescape.medicinereminder.data.db.MedicineShape
import com.wwwescape.medicinereminder.data.schedule.ScheduledDose
import com.wwwescape.medicinereminder.ui.icons.MedicineArt
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Tells the Wear OS companion when a reminder starts and stops ringing. Fire-and-forget: with no
 * watch, no companion app, or no Google Play services, nothing happens and the phone's own
 * reminder is unaffected.
 */
object WearBridge {

    private const val TAG = "WearBridge"
    private const val ICON_PX = 160
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun showReminder(context: Context, slot: String, minute: Int, doses: List<ScheduledDose>, test: Boolean) {
        val app = context.applicationContext
        val payload = JSONObject()
            .put(WearProtocol.KEY_SLOT, slot)
            .put(WearProtocol.KEY_TEST, test)
            .put(WearProtocol.KEY_TIME, formatMinute(app, minute))
            .put(
                WearProtocol.KEY_DOSES,
                JSONArray(
                    doses.map { dose ->
                        val medicine = dose.medicine
                        JSONObject()
                            .put(WearProtocol.KEY_NAME, medicine.name)
                            .put(WearProtocol.KEY_DOSE, doseText(app, medicine))
                            .put(WearProtocol.KEY_INSTRUCTIONS, medicine.instructions)
                            .put(WearProtocol.KEY_ICON, iconPng(medicine.shape, medicine.color1, medicine.color2))
                    },
                ),
            )
        send(app, WearProtocol.PATH_SHOW, payload)
    }

    fun dismissReminder(context: Context, slot: String) {
        send(context.applicationContext, WearProtocol.PATH_DISMISS, JSONObject().put(WearProtocol.KEY_SLOT, slot))
    }

    private fun send(context: Context, path: String, payload: JSONObject) {
        scope.launch {
            try {
                val nodes = Wearable.getCapabilityClient(context)
                    .getCapability(WearProtocol.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await()
                    .nodes
                val bytes = payload.toString().toByteArray(Charsets.UTF_8)
                nodes.forEach { node -> Wearable.getMessageClient(context).sendMessage(node.id, path, bytes).await() }
            } catch (e: Exception) {
                // No Play services, no paired watch, or the watch app isn't installed.
                Log.d(TAG, "Not sent to a watch: ${e.message}")
            }
        }
    }

    private fun iconPng(shape: MedicineShape, color1: Int, color2: Int): String {
        val bitmap = MedicineArt.bitmap(shape, color1, color2, ICON_PX)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
