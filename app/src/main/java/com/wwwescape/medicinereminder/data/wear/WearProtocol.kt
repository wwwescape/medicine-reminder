package com.wwwescape.medicinereminder.data.wear

/**
 * What the phone and the Wear OS companion say to each other over the Data Layer. Kept in step
 * by hand with wear/…/WearProtocol.kt: change both together.
 *
 * - [PATH_SHOW] phone → watch: a dose slot is ringing. JSON: [KEY_SLOT], [KEY_TEST], [KEY_TIME],
 *   [KEY_DOSES] (each with [KEY_NAME], [KEY_DOSE], [KEY_INSTRUCTIONS], [KEY_ICON] as base64 PNG).
 * - [PATH_DISMISS] phone → watch: the slot was answered, snoozed or closed. JSON: [KEY_SLOT].
 * - [PATH_ANSWER] watch → phone: the user answered on the watch. JSON: [KEY_SLOT], [KEY_TEST],
 *   [KEY_ACTION] (one of [ACTION_TAKE], [ACTION_SKIP], [ACTION_SNOOZE], [ACTION_CLOSE]).
 */
object WearProtocol {
    /** Declared by the watch app (wear/src/main/res/values/wear.xml) so the phone can find it. */
    const val CAPABILITY = "medicine_reminder_watch"

    const val PATH_SHOW = "/reminder/show"
    const val PATH_DISMISS = "/reminder/dismiss"
    const val PATH_ANSWER = "/reminder/answer"

    const val KEY_SLOT = "slot"
    const val KEY_TEST = "test"
    const val KEY_TIME = "time"
    const val KEY_DOSES = "doses"
    const val KEY_NAME = "name"
    const val KEY_DOSE = "dose"
    const val KEY_INSTRUCTIONS = "instructions"
    const val KEY_ICON = "icon"
    const val KEY_ACTION = "action"

    const val ACTION_TAKE = "TAKE"
    const val ACTION_SKIP = "SKIP"
    const val ACTION_SNOOZE = "SNOOZE"
    const val ACTION_CLOSE = "CLOSE"

    /** The Settings test reminder's slot id. */
    const val TEST_SLOT = "test"

    /** A dose slot as "epochDay:minute". */
    fun slotId(epochDay: Long, minute: Int) = "$epochDay:$minute"

    fun parseSlot(slot: String): Pair<Long, Int>? =
        slot.split(':').takeIf { it.size == 2 }?.let { (day, minute) -> day.toLongOrNull()?.let { d -> minute.toIntOrNull()?.let { d to it } } }
}
