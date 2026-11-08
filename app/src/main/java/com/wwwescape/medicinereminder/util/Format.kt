package com.wwwescape.medicinereminder.util

import android.content.Context
import android.text.format.DateFormat
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DoseUnit
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.MedicineShape
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "1", "0.5", "1.5" — whole numbers without a trailing ".0". */
fun formatAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toBigDecimal().stripTrailingZeros().toPlainString()

fun formatMinute(context: Context, minute: Int): String {
    val time = LocalTime.of(minute / 60, minute % 60)
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"))

/** "2 drops", "1 tablet", "0.5 tablets". */
fun unitText(context: Context, unit: DoseUnit, amount: Double): String {
    val count = if (amount == 1.0) 1 else 2
    return context.resources.getQuantityString(unit.pluralRes, count, formatAmount(amount))
}

/** "2 drops" or, with a strength, "2 drops · 0.5%". */
fun doseText(context: Context, medicine: Medicine): String =
    listOf(unitText(context, medicine.doseUnit, medicine.doseAmount), medicine.strength)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

fun scheduleText(context: Context, medicine: Medicine): String {
    val days = when (medicine.frequency) {
        Frequency.DAILY -> context.getString(R.string.frequency_daily)
        Frequency.EVERY_X_DAYS -> context.resources.getQuantityString(
            R.plurals.frequency_every_x_days_summary, medicine.intervalDays, medicine.intervalDays,
        )
        Frequency.WEEKDAYS -> DayOfWeek.entries
            .filter { medicine.weekdays and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        Frequency.AS_NEEDED -> return context.getString(R.string.frequency_as_needed)
    }
    val times = medicine.times.joinToString(", ") { formatMinute(context, it) }
    return if (times.isEmpty()) days else "$days · $times"
}

/** "Day 12 of 30", "Starts 3 Dec", "Finished 1 Nov", "Day 40 · ongoing". */
fun courseProgressText(context: Context, course: Course, today: LocalDate = LocalDate.now()): String {
    val end = course.endDate
    return when {
        today < course.startDate -> context.getString(R.string.course_starts, formatShortDate(course.startDate))
        end != null && today > end -> context.getString(R.string.course_finished, formatShortDate(end))
        end == null -> context.getString(
            R.string.course_day_ongoing, ChronoUnit.DAYS.between(course.startDate, today) + 1,
        )
        else -> context.getString(
            R.string.course_day_of,
            ChronoUnit.DAYS.between(course.startDate, today) + 1,
            ChronoUnit.DAYS.between(course.startDate, end) + 1,
        )
    }
}

/** "Today, 10:30 AM", "Tomorrow, 10:30 AM", "Tue, 12 Nov, 10:30 AM". */
fun formatAppointmentWhen(context: Context, appointment: Appointment, today: LocalDate = LocalDate.now()): String {
    val day = when (appointment.date) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        else -> appointment.date.format(
            DateTimeFormatter.ofPattern(if (appointment.date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"),
        )
    }
    return "$day, ${formatMinute(context, appointment.minute)}"
}

val DoseUnit.pluralRes: Int
    get() = when (this) {
        DoseUnit.TABLET -> R.plurals.unit_tablet
        DoseUnit.CAPSULE -> R.plurals.unit_capsule
        DoseUnit.DROP -> R.plurals.unit_drop
        DoseUnit.PUFF -> R.plurals.unit_puff
        DoseUnit.SPRAY -> R.plurals.unit_spray
        DoseUnit.ML -> R.plurals.unit_ml
        DoseUnit.MG -> R.plurals.unit_mg
        DoseUnit.UNIT -> R.plurals.unit_unit
        DoseUnit.APPLICATION -> R.plurals.unit_application
        DoseUnit.PATCH -> R.plurals.unit_patch
        DoseUnit.INJECTION -> R.plurals.unit_injection
        DoseUnit.SACHET -> R.plurals.unit_sachet
        DoseUnit.SUPPOSITORY -> R.plurals.unit_suppository
        DoseUnit.TEASPOON -> R.plurals.unit_teaspoon
        DoseUnit.TABLESPOON -> R.plurals.unit_tablespoon
        DoseUnit.DOSE -> R.plurals.unit_dose
        DoseUnit.SESSION -> R.plurals.unit_session
        DoseUnit.RESPULE -> R.plurals.unit_respule
        DoseUnit.MINUTE -> R.plurals.unit_minute
        DoseUnit.GRAM -> R.plurals.unit_gram
        DoseUnit.PUMP -> R.plurals.unit_pump
    }

val MedicineShape.labelRes: Int
    get() = when (this) {
        MedicineShape.ROUND -> R.string.shape_round
        MedicineShape.ROUND_SCORED -> R.string.shape_round_scored
        MedicineShape.ROUND_HALVES -> R.string.shape_round_halves
        MedicineShape.OVAL -> R.string.shape_oval
        MedicineShape.CAPLET -> R.string.shape_caplet
        MedicineShape.CAPLET_SCORED -> R.string.shape_caplet_scored
        MedicineShape.CAPSULE -> R.string.shape_capsule
        MedicineShape.SOFTGEL -> R.string.shape_softgel
        MedicineShape.TRIANGLE -> R.string.shape_triangle
        MedicineShape.SQUARE -> R.string.shape_square
        MedicineShape.DIAMOND -> R.string.shape_diamond
        MedicineShape.HEXAGON -> R.string.shape_hexagon
        MedicineShape.HEART -> R.string.shape_heart
        MedicineShape.DROPS -> R.string.shape_drops
        MedicineShape.SYRUP -> R.string.shape_syrup
        MedicineShape.NASAL_SPRAY -> R.string.shape_nasal_spray
        MedicineShape.INHALER -> R.string.shape_inhaler
        MedicineShape.NEBULIZER -> R.string.shape_nebulizer
        MedicineShape.SYRINGE -> R.string.shape_syringe
        MedicineShape.PEN -> R.string.shape_pen
        MedicineShape.VIAL -> R.string.shape_vial
        MedicineShape.AMPOULE -> R.string.shape_ampoule
        MedicineShape.TUBE -> R.string.shape_tube
        MedicineShape.PATCH -> R.string.shape_patch
        MedicineShape.SACHET -> R.string.shape_sachet
        MedicineShape.SUPPOSITORY -> R.string.shape_suppository
    }
