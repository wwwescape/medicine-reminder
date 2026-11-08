package com.wwwescape.medicinereminder.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A course of treatment — one prescription or episode of care. Every medicine belongs to a
 * course, and the course's dates are its medicines' default dates. */
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** What the course treats ("Dry eyes"), optional. */
    @ColumnInfo(defaultValue = "") val condition: String = "",
    /** Kept in the version-1 "prescriber" column, which held a doctor or clinic. */
    @ColumnInfo(name = "prescriber") val doctorName: String = "",
    /** Specialty or department ("Ophthalmologist"). */
    @ColumnInfo(defaultValue = "") val doctorSpecialty: String = "",
    @ColumnInfo(defaultValue = "") val doctorPhone: String = "",
    @ColumnInfo(defaultValue = "") val clinicName: String = "",
    @ColumnInfo(defaultValue = "") val clinicAddress: String = "",
    @ColumnInfo(defaultValue = "") val clinicPhone: String = "",
    /** Patient or file number at the clinic, often asked for at the desk. */
    @ColumnInfo(defaultValue = "") val patientId: String = "",
    val notes: String = "",
    val startDate: LocalDate,
    /** Inclusive last day, or null for an ongoing course. */
    val endDate: LocalDate?,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A photo or PDF of a prescription, copied into app storage (`files/prescriptions/[fileName]`). */
@Entity(
    tableName = "prescription_files",
    foreignKeys = [ForeignKey(Course::class, ["id"], ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId")],
)
data class PrescriptionFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: Long = System.currentTimeMillis(),
)

enum class Frequency { DAILY, EVERY_X_DAYS, WEEKDAYS, AS_NEEDED }

@Entity(
    tableName = "medicines",
    foreignKeys = [ForeignKey(Course::class, ["id"], ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId")],
)
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val name: String,
    /** Free text such as "500 mg" or "0.5%". */
    val strength: String = "",
    val shape: MedicineShape = MedicineShape.ROUND,
    /** ARGB colors of the icon; [color2] is only drawn by two-tone shapes. */
    val color1: Int,
    val color2: Int,
    val doseAmount: Double = 1.0,
    val doseUnit: DoseUnit = DoseUnit.TABLET,
    /** "After food", "In both eyes" and so on. */
    val instructions: String = "",
    val notes: String = "",
    val frequency: Frequency = Frequency.DAILY,
    /** For [Frequency.EVERY_X_DAYS]: 2 = every other day. */
    val intervalDays: Int = 1,
    /** For [Frequency.WEEKDAYS]: bit 0 = Monday … bit 6 = Sunday. */
    val weekdays: Int = 0,
    /** Reminder times as minutes after midnight, sorted. Empty for [Frequency.AS_NEEDED]. */
    val times: List<Int> = emptyList(),
    /** Null = the course's start date. */
    val startDate: LocalDate? = null,
    /** Inclusive; null = until the course ends. */
    val endDate: LocalDate? = null,
    val remindersEnabled: Boolean = true,
    /** Photo of the pack — box, strip, bottle or tube — in `files/photos/`. */
    val packagePhoto: String? = null,
    /** Photo of the dose itself — the tablet, the syrup in its spoon — to check against before
     * taking it. In `files/photos/`. */
    val dosePhoto: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class DoseStatus { TAKEN, SKIPPED }

/** What happened to one scheduled dose, keyed by medicine, local date and minute of day so a
 * time-zone change doesn't shift history. A scheduled dose with no row is pending or missed.
 * As-needed doses are logged at the minute they were taken. */
@Entity(
    tableName = "dose_log",
    primaryKeys = ["medicineId", "date", "minute"],
    foreignKeys = [ForeignKey(Medicine::class, ["id"], ["medicineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class DoseLog(
    val medicineId: Long,
    val date: LocalDate,
    val minute: Int,
    val status: DoseStatus,
    val amount: Double,
    val loggedAt: Long = System.currentTimeMillis(),
)

/** A doctor's visit, usually a follow-up for a course, with reminders ahead of it. */
@Entity(
    tableName = "appointments",
    foreignKeys = [ForeignKey(Course::class, ["id"], ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId"), Index("date")],
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for a visit that isn't tied to a course. */
    val courseId: Long?,
    val title: String,
    val doctorName: String = "",
    val place: String = "",
    val date: LocalDate,
    /** Minutes after midnight. */
    val minute: Int,
    /** "Bring the old prescription", "Come fasting". */
    val notes: String = "",
    /** How long before the visit to remind, in minutes (1440 = a day before). */
    val reminders: List<Int> = listOf(24 * 60, 2 * 60),
    val createdAt: Long = System.currentTimeMillis(),
)

/** What a stored document is, for grouping and filtering. Stored by name. */
enum class DocumentCategory {
    PRESCRIPTION, GLASSES, LAB_REPORT, IMAGING, DISCHARGE, VACCINATION, INSURANCE, BILL, OTHER,
}

/**
 * A health document kept on its own, outside any course: a glasses prescription, a lab report,
 * an insurance card. Its file lives in `files/documents/[fileName]` and belongs to the document
 * alone — one copied from a course keeps its copy when the course is deleted.
 */
@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(Course::class, ["id"], ["sourceCourseId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("sourceCourseId"), Index("date")],
)
data class HealthDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: DocumentCategory = DocumentCategory.OTHER,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
    /** When the document was issued, for sorting; defaults to the day it was added. */
    val date: LocalDate,
    val notes: String = "",
    /** The course it was copied from, if any (cleared if that course is deleted). */
    val sourceCourseId: Long? = null,
    /** The course file it was copied from, so the course can show it's already saved. */
    val sourcePrescriptionId: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
