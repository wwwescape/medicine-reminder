package com.wwwescape.medicinereminder.data.schedule

import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DoseLog
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.db.Medicine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** One dose slot: a medicine at a minute of a day. */
data class DoseKey(val medicineId: Long, val date: LocalDate, val minute: Int) {
    val dateTime: LocalDateTime get() = date.atTime(LocalTime.of(minute / 60, minute % 60))
}

fun DoseLog.key() = DoseKey(medicineId, date, minute)

/** A scheduled dose together with what has happened to it so far. */
data class ScheduledDose(
    val medicine: Medicine,
    val course: Course,
    val date: LocalDate,
    val minute: Int,
    val log: DoseLog?,
) {
    val key get() = DoseKey(medicine.id, date, minute)
}

/** Pure scheduling rules, shared by the UI, the alarm scheduler and the tests. */
object Schedule {

    fun startOf(medicine: Medicine, course: Course): LocalDate = medicine.startDate ?: course.startDate

    /** Inclusive, or null when it runs as long as an ongoing course. */
    fun endOf(medicine: Medicine, course: Course): LocalDate? = medicine.endDate ?: course.endDate

    fun isActiveOn(medicine: Medicine, course: Course, date: LocalDate): Boolean {
        if (date < startOf(medicine, course)) return false
        val end = endOf(medicine, course)
        return end == null || date <= end
    }

    /** Whether [medicine] has reminder times on [date]. As-needed medicines never do. */
    fun isScheduledOn(medicine: Medicine, course: Course, date: LocalDate): Boolean {
        if (!isActiveOn(medicine, course, date) || medicine.times.isEmpty()) return false
        return when (medicine.frequency) {
            Frequency.DAILY -> true
            Frequency.EVERY_X_DAYS -> {
                val interval = medicine.intervalDays.coerceAtLeast(1)
                ChronoUnit.DAYS.between(startOf(medicine, course), date) % interval == 0L
            }
            Frequency.WEEKDAYS -> medicine.weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0
            Frequency.AS_NEEDED -> false
        }
    }

    /** Every scheduled dose on [date], ordered by time, joined with its log entry if any. */
    fun dosesOn(
        date: LocalDate,
        medicines: List<Medicine>,
        courses: Map<Long, Course>,
        logs: Map<DoseKey, DoseLog>,
    ): List<ScheduledDose> = medicines.flatMap { medicine ->
        val course = courses[medicine.courseId] ?: return@flatMap emptyList()
        if (!isScheduledOn(medicine, course, date)) return@flatMap emptyList()
        medicine.times.map { minute ->
            ScheduledDose(medicine, course, date, minute, logs[DoseKey(medicine.id, date, minute)])
        }
    }.sortedWith(compareBy({ it.minute }, { it.medicine.name.lowercase() }))

    /** The earliest dose time strictly after [after] that has reminders on and isn't logged yet,
     * looking at most [horizonDays] ahead. */
    fun nextReminder(
        after: LocalDateTime,
        medicines: List<Medicine>,
        courses: Map<Long, Course>,
        logged: Set<DoseKey>,
        horizonDays: Long = 400,
    ): LocalDateTime? {
        val withReminders = medicines.filter { it.remindersEnabled && it.frequency != Frequency.AS_NEEDED && it.times.isNotEmpty() }
        if (withReminders.isEmpty()) return null
        var date = after.toLocalDate()
        val last = date.plusDays(horizonDays)
        while (date <= last) {
            val candidate = withReminders.flatMap { medicine ->
                val course = courses[medicine.courseId] ?: return@flatMap emptyList()
                if (!isScheduledOn(medicine, course, date)) return@flatMap emptyList()
                medicine.times.map { DoseKey(medicine.id, date, it) }
            }
                .filter { it.dateTime > after && it !in logged }
                .minOfOrNull { it.dateTime }
            if (candidate != null) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    /** Doses with reminders on at exactly [date]/[minute] that nobody has answered yet. */
    fun unansweredAt(
        date: LocalDate,
        minute: Int,
        medicines: List<Medicine>,
        courses: Map<Long, Course>,
        logs: Map<DoseKey, DoseLog>,
    ): List<ScheduledDose> = dosesOn(date, medicines.filter { it.remindersEnabled }, courses, logs)
        .filter { it.minute == minute && it.log == null }
}

/** How well a course has been followed so far: doses due up to [now] and how many were taken. */
data class Adherence(val due: Int, val taken: Int, val skipped: Int) {
    val missed get() = due - taken - skipped
    val fraction: Float? get() = if (due == 0) null else taken.toFloat() / due
}

fun adherence(
    now: LocalDateTime,
    medicines: List<Medicine>,
    courses: Map<Long, Course>,
    logs: Map<DoseKey, DoseLog>,
    from: LocalDate,
): Adherence {
    var due = 0
    var taken = 0
    var skipped = 0
    var date = from
    while (date <= now.toLocalDate()) {
        Schedule.dosesOn(date, medicines, courses, logs)
            .filter { it.key.dateTime <= now }
            .forEach { dose ->
                due++
                when (dose.log?.status) {
                    DoseStatus.TAKEN -> taken++
                    DoseStatus.SKIPPED -> skipped++
                    null -> Unit
                }
            }
        date = date.plusDays(1)
    }
    return Adherence(due, taken, skipped)
}
