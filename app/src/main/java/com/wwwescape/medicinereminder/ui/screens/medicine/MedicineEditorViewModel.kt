package com.wwwescape.medicinereminder.ui.screens.medicine

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DoseUnit
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.MedicineShape
import com.wwwescape.medicinereminder.ui.icons.MedicineColors
import com.wwwescape.medicinereminder.util.formatAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToLong

enum class PhotoSlot { PACKAGE, DOSE }

/** The most doses a day the counter goes to; eye drops after surgery can be hourly. */
const val MAX_TIMES_PER_DAY = 12

/**
 * Sensible starting times for [count] doses a day, as minutes after midnight: spread over the
 * waking day (8 AM–8 PM for up to four, wider for more). Each time can then be changed.
 */
fun defaultTimes(count: Int): List<Int> {
    val hours = when (count) {
        1 -> listOf(8)
        2 -> listOf(8, 20)
        3 -> listOf(8, 14, 20)
        4 -> listOf(8, 12, 16, 20)
        else -> null
    }
    if (hours != null) return hours.map { it * 60 }
    // Five or more: evenly from 7 AM to 10 PM, rounded down to the half hour.
    val first = 7 * 60
    val span = 15 * 60
    return (0 until count).map { i -> (first + span * i / (count - 1)) / 30 * 30 }
}

class MedicineEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private var original: Medicine? = null
    private var loaded = false

    var course by mutableStateOf<Course?>(null)
        private set
    var name by mutableStateOf("")
    var strength by mutableStateOf("")
    var shape by mutableStateOf(MedicineShape.ROUND)
        private set
    var color1 by mutableStateOf(MedicineColors[0])
    var color2 by mutableStateOf(MedicineColors[15])
    /** As typed; see [doseAmount]. */
    var amountText by mutableStateOf("1")
    var doseUnit by mutableStateOf(DoseUnit.TABLET)
    var instructions by mutableStateOf("")
    var notes by mutableStateOf("")
    var frequency by mutableStateOf(Frequency.DAILY)
    var intervalDays by mutableStateOf("2")
    var weekdays by mutableStateOf(0)
    /** The day's dose times, sorted; their count is the "times a day". */
    val times = mutableStateListOf(8 * 60)
    /** The body area picked for the instructions (a group's label), 0 for none picked, or null
     * to infer it from the instructions themselves. */
    var instructionArea by mutableStateOf<Int?>(null)
    /** Null = follow the course's dates. */
    var startDate by mutableStateOf<LocalDate?>(null)
    var endDate by mutableStateOf<LocalDate?>(null)
    var remindersEnabled by mutableStateOf(true)
    var packagePhoto by mutableStateOf<String?>(null)
        private set
    var dosePhoto by mutableStateOf<String?>(null)
        private set
    /** Photos taken or picked in this editor. Kept only if the medicine is saved. */
    private val addedPhotos = mutableSetOf<String>()
    private var saved = false
    var saving by mutableStateOf(false)
        private set
    /** The unit follows the shape until the user picks one themselves. */
    private var unitChosen = false

    val isNew get() = original == null

    /** Whether this medicine was started as the next step of another (a taper). */
    var isNextStep by mutableStateOf(false)
        private set

    val amountValid: Boolean get() = parsedAmount != null
    val doseAmount: Double get() = parsedAmount ?: 1.0
    private val parsedAmount: Double?
        get() = amountText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 10_000 }

    val canSave: Boolean
        // A next step without its own start would overlap the previous step and double its doses.
        get() = name.isNotBlank() && amountValid && !saving && course != null && !(isNextStep && startDate == null) && when (frequency) {
            Frequency.AS_NEEDED -> true
            Frequency.EVERY_X_DAYS -> times.isNotEmpty() && (intervalDays.toIntOrNull() ?: 0) >= 1
            Frequency.WEEKDAYS -> times.isNotEmpty() && weekdays != 0
            Frequency.DAILY -> times.isNotEmpty()
        }

    /** Loads [medicineId] to edit, or — with [copyOf] — starts a new medicine as the next step
     * of that one: everything copied, starting the day after it ends. */
    fun load(courseId: Long, medicineId: Long, copyOf: Long = 0) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            course = MedicineRepository.getCourse(app, courseId)
            val sourceId = if (copyOf != 0L) copyOf else medicineId
            val medicine = if (sourceId != 0L) MedicineRepository.getMedicine(app, sourceId) else null
            if (medicine != null) {
                if (copyOf == 0L) original = medicine
                name = medicine.name
                strength = medicine.strength
                shape = medicine.shape
                color1 = medicine.color1
                color2 = medicine.color2
                amountText = formatAmount(medicine.doseAmount)
                doseUnit = medicine.doseUnit
                unitChosen = true
                instructions = medicine.instructions
                notes = medicine.notes
                frequency = medicine.frequency
                intervalDays = medicine.intervalDays.toString()
                weekdays = medicine.weekdays
                times.clear()
                times.addAll(medicine.times)
                startDate = medicine.startDate
                endDate = medicine.endDate
                remindersEnabled = medicine.remindersEnabled
                packagePhoto = medicine.packagePhoto
                dosePhoto = medicine.dosePhoto
                if (copyOf != 0L) {
                    isNextStep = true
                    startDate = medicine.endDate?.plusDays(1)
                    endDate = null
                    // Each medicine owns its photo files, so the copy gets its own.
                    packagePhoto = medicine.packagePhoto?.let { duplicatePhoto(it) }
                    dosePhoto = medicine.dosePhoto?.let { duplicatePhoto(it) }
                }
            }
        }
    }

    private suspend fun duplicatePhoto(fileName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val copy = MedicineRepository.newPhotoFile(app)
            MedicineRepository.photoFile(app, fileName).copyTo(copy, overwrite = true)
            addedPhotos += copy.name
            copy.name
        }.getOrNull()
    }

    fun selectShape(value: MedicineShape) {
        if (value != shape) instructionArea = null
        shape = value
        if (!unitChosen) doseUnit = value.defaultUnit
    }

    fun selectUnit(value: DoseUnit) {
        doseUnit = value
        unitChosen = true
    }

    /** One step up or down in the unit's own step size, snapping to a whole step first. */
    fun changeAmount(direction: Int) {
        val step = doseUnit.step
        val steps = (doseAmount / step).roundToLong() + direction
        amountText = formatAmount((steps * step).coerceIn(step, 10_000.0))
    }

    /** Changes one dose time. Returns false, changing nothing, if that time is already taken. */
    fun setTime(index: Int, minute: Int): Boolean {
        if (times.withIndex().any { (i, t) -> i != index && t == minute }) return false
        times[index] = minute
        val sorted = times.sorted()
        times.clear()
        times.addAll(sorted)
        return true
    }

    /** Sets how many doses a day, starting the times from [defaultTimes]. */
    fun setTimesPerDay(count: Int) {
        val clamped = count.coerceIn(1, MAX_TIMES_PER_DAY)
        if (clamped == times.size) return
        times.clear()
        times.addAll(defaultTimes(clamped))
    }

    fun removeInstructions(texts: Collection<String>) {
        instructions = instructions.split(",").map { it.trim() }
            .filter { part -> part.isNotEmpty() && texts.none { it.equals(part, ignoreCase = true) } }
            .joinToString(", ")
    }

    fun toggleWeekday(bit: Int) {
        weekdays = weekdays xor (1 shl bit)
    }

    /** Adds or removes a quick instruction. Picking one of [replaces] (such as "Left eye" over
     * "Both eyes") drops the others first, so the instructions never contradict themselves. */
    fun toggleInstruction(text: String, replaces: List<String> = emptyList()) {
        val parts = instructions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val selected = parts.any { it.equals(text, ignoreCase = true) }
        instructions = (
            if (selected) {
                parts.filterNot { it.equals(text, ignoreCase = true) }
            } else {
                parts.filterNot { part -> replaces.any { it.equals(part, ignoreCase = true) } } + text
            }
            ).joinToString(", ")
    }

    /** Sets a slot to a photo already in app storage, or clears it with null. */
    fun setPhoto(slot: PhotoSlot, fileName: String?) {
        val previous = if (slot == PhotoSlot.PACKAGE) packagePhoto else dosePhoto
        if (slot == PhotoSlot.PACKAGE) packagePhoto = fileName else dosePhoto = fileName
        if (fileName != null) addedPhotos += fileName
        // A photo from this session that's been replaced is unreferenced; the saved one stays
        // until the save goes through.
        if (previous != null && previous in addedPhotos) discard(previous)
    }

    fun addPhoto(slot: PhotoSlot, uri: android.net.Uri) {
        viewModelScope.launch { MedicineRepository.importPhoto(app, uri)?.let { setPhoto(slot, it) } }
    }

    fun discard(fileName: String) {
        addedPhotos -= fileName
        viewModelScope.launch { MedicineRepository.deletePhoto(app, fileName) }
    }

    override fun onCleared() {
        // Abandoned without saving: drop the photos that were only ever in this editor. The
        // ViewModel scope is already cancelled here; the deletes are quick file ops.
        if (!saved) addedPhotos.forEach { MedicineRepository.photoFile(app, it).delete() }
    }

    /** The medicine as currently edited: what Save stores, and what the preview describes. */
    fun draft(): Medicine =
        (original ?: Medicine(courseId = course?.id ?: 0, name = "", color1 = color1, color2 = color2)).copy(
            name = name.trim(),
            strength = strength.trim(),
            shape = shape,
            color1 = color1,
            color2 = color2,
            doseAmount = doseAmount,
            doseUnit = doseUnit,
            instructions = instructions.trim(),
            notes = notes.trim(),
            frequency = frequency,
            intervalDays = intervalDays.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            weekdays = weekdays,
            times = if (frequency == Frequency.AS_NEEDED) emptyList() else times.toList(),
            startDate = startDate,
            endDate = endDate,
            remindersEnabled = remindersEnabled,
            packagePhoto = packagePhoto,
            dosePhoto = dosePhoto,
        )

    fun save(onSaved: () -> Unit) {
        if (course == null || !canSave) return
        saving = true
        viewModelScope.launch {
            MedicineRepository.saveMedicine(app, draft())
            saved = true
            original?.let { old ->
                listOfNotNull(old.packagePhoto, old.dosePhoto)
                    .filter { it != packagePhoto && it != dosePhoto }
                    .forEach { MedicineRepository.deletePhoto(app, it) }
            }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val medicine = original ?: return
        viewModelScope.launch {
            MedicineRepository.deleteMedicine(app, medicine)
            onDeleted()
        }
    }
}
