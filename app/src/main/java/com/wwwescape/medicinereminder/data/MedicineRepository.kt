package com.wwwescape.medicinereminder.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DoseLog
import com.wwwescape.medicinereminder.data.db.DocumentCategory
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.HealthDocument
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.MedicineDatabase
import com.wwwescape.medicinereminder.data.db.PrescriptionFile
import com.wwwescape.medicinereminder.data.reminders.AppointmentReminders
import com.wwwescape.medicinereminder.data.reminders.Reminders
import com.wwwescape.medicinereminder.data.schedule.DoseKey
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import com.wwwescape.medicinereminder.data.schedule.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Everything the screens derive their state from, kept current. The data is small (a few
 * courses, a few thousand log rows at most), so screens filter it in memory. */
data class Snapshot(
    val courses: Map<Long, Course> = emptyMap(),
    val medicines: List<Medicine> = emptyList(),
    val logs: Map<DoseKey, DoseLog> = emptyMap(),
    /** Follow-up visits, soonest first. */
    val appointments: List<Appointment> = emptyList(),
    val loaded: Boolean = false,
)

/** A prescription copied into app storage but not yet attached to a saved course. */
data class StoredFile(val fileName: String, val displayName: String, val mimeType: String)

/** The single entry point for reading and changing courses, medicines and the dose log. Every
 * change that can move the next reminder reschedules it. */
object MedicineRepository {

    private const val PRESCRIPTIONS_DIR = "prescriptions"
    private const val PHOTOS_DIR = "photos"
    private const val DOCUMENTS_DIR = "documents"
    private const val BACKFILL_AFTER_MILLIS = 12 * 60 * 60 * 1000L

    private fun db(context: Context) = MedicineDatabase.get(context)

    fun snapshot(context: Context): Flow<Snapshot> = combine(
        db(context).courses().observeAll(),
        db(context).medicines().observeAll(),
        db(context).doseLog().observeAll(),
        db(context).appointments().observeAll(),
    ) { courses, medicines, logs, appointments ->
        Snapshot(courses.associateBy { it.id }, medicines, logs.associateBy { it.key() }, appointments, loaded = true)
    }

    fun courses(context: Context): Flow<List<Course>> = db(context).courses().observeAll()
    fun course(context: Context, id: Long): Flow<Course?> = db(context).courses().observe(id)
    suspend fun getCourse(context: Context, id: Long): Course? = db(context).courses().get(id)
    fun medicines(context: Context): Flow<List<Medicine>> = db(context).medicines().observeAll()
    fun medicinesForCourse(context: Context, courseId: Long): Flow<List<Medicine>> =
        db(context).medicines().observeForCourse(courseId)
    suspend fun getMedicine(context: Context, id: Long): Medicine? = db(context).medicines().get(id)
    fun logsBetween(context: Context, from: LocalDate, to: LocalDate): Flow<List<DoseLog>> =
        db(context).doseLog().observeBetween(from, to)
    fun logsForCourse(context: Context, courseId: Long): Flow<List<DoseLog>> =
        db(context).doseLog().observeForCourse(courseId)
    fun allLogs(context: Context): Flow<List<DoseLog>> = db(context).doseLog().observeAll()
    fun prescriptions(context: Context, courseId: Long): Flow<List<PrescriptionFile>> =
        db(context).prescriptionFiles().observeForCourse(courseId)

    suspend fun saveCourse(context: Context, course: Course): Long {
        val id = if (course.id == 0L) {
            db(context).courses().insert(course)
        } else {
            db(context).courses().update(course)
            course.id
        }
        Reminders.reschedule(context)
        return id
    }

    suspend fun deleteCourse(context: Context, course: Course) {
        val files = db(context).prescriptionFiles().getForCourse(course.id)
        val photos = db(context).medicines().getAll().filter { it.courseId == course.id }.flatMap { it.photos() }
        db(context).courses().delete(course)
        files.forEach { deleteStoredFile(context, it.fileName) }
        photos.forEach { deletePhoto(context, it) }
        Reminders.reschedule(context)
        AppointmentReminders.reschedule(context)
    }

    suspend fun saveMedicine(context: Context, medicine: Medicine): Long {
        val rowId = db(context).medicines().upsert(medicine)
        Reminders.reschedule(context)
        return if (medicine.id != 0L) medicine.id else rowId
    }

    suspend fun deleteMedicine(context: Context, medicine: Medicine) {
        db(context).medicines().delete(medicine)
        medicine.photos().forEach { deletePhoto(context, it) }
        Reminders.reschedule(context)
    }

    suspend fun getAppointment(context: Context, id: Long): Appointment? = db(context).appointments().get(id)

    suspend fun saveAppointment(context: Context, appointment: Appointment) {
        db(context).appointments().upsert(appointment)
        AppointmentReminders.reschedule(context)
    }

    suspend fun deleteAppointment(context: Context, appointment: Appointment) {
        db(context).appointments().delete(appointment)
        AppointmentReminders.reschedule(context)
    }

    /** A new, empty file in app storage for the camera to write a medicine photo into. */
    fun newPhotoFile(context: Context): File =
        photoFile(context, "${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }

    /** Copies a picked medicine photo into app storage; returns its file name. */
    suspend fun importPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = newPhotoFile(context)
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            target.name
        }.getOrNull()
    }

    fun photoFile(context: Context, fileName: String): File = File(File(context.filesDir, PHOTOS_DIR), fileName)

    suspend fun deletePhoto(context: Context, fileName: String) = withContext(Dispatchers.IO) {
        photoFile(context, fileName).delete()
    }

    suspend fun logDose(context: Context, key: DoseKey, status: DoseStatus, amount: Double) {
        // Filling in a dose long after the fact: record it at its scheduled time rather than
        // "now", which would read as taken days late.
        val scheduled = key.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val loggedAt = if (now - scheduled > BACKFILL_AFTER_MILLIS) scheduled else now
        db(context).doseLog().upsert(DoseLog(key.medicineId, key.date, key.minute, status, amount, loggedAt))
        Reminders.onSlotChanged(context, key.date, key.minute)
    }

    suspend fun clearDose(context: Context, key: DoseKey) {
        db(context).doseLog().delete(key.medicineId, key.date, key.minute)
        Reminders.reschedule(context)
    }

    /** Copies a picked image or PDF into app storage, so it survives the source being moved or
     * the picker's permission expiring. */
    suspend fun importPrescription(context: Context, uri: Uri): StoredFile? =
        importInto(context, uri, File(context.filesDir, PRESCRIPTIONS_DIR), "Prescription")

    /** Copies [uri] into [dir] under a fresh name, keeping its display name and type. */
    private suspend fun importInto(context: Context, uri: Uri, dir: File, fallbackName: String): StoredFile? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?: fallbackName
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
            val fileName = "${UUID.randomUUID()}.$extension"
            val target = File(dir, fileName)
            target.parentFile?.mkdirs()
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            StoredFile(fileName, displayName, mimeType)
        }.getOrNull()
    }

    /** Attaches a stored file to a course and, when the user keeps prescriptions in Documents
     * too (Settings: [com.wwwescape.medicinereminder.data.settings.AppSettings.saveToDocuments]),
     * copies it there. */
    suspend fun attachPrescription(context: Context, courseId: Long, file: StoredFile) {
        val prescription = PrescriptionFile(courseId = courseId, fileName = file.fileName, displayName = file.displayName, mimeType = file.mimeType)
        val id = db(context).prescriptionFiles().insert(prescription)
        if (SettingsRepository.current(context).saveToDocuments) {
            db(context).courses().get(courseId)?.let { copyToDocuments(context, prescription.copy(id = id), it) }
        }
    }

    fun documents(context: Context): Flow<List<HealthDocument>> = db(context).documents().observeAll()
    suspend fun getDocument(context: Context, id: Long): HealthDocument? = db(context).documents().get(id)

    /** Adds a picked image or PDF to Documents; returns the new document's id. */
    suspend fun importDocument(context: Context, uri: Uri, category: DocumentCategory): Long? {
        val stored = importInto(context, uri, File(context.filesDir, DOCUMENTS_DIR), "Document") ?: return null
        return db(context).documents().upsert(
            HealthDocument(
                title = stored.displayName.substringBeforeLast('.'),
                category = category,
                fileName = stored.fileName,
                displayName = stored.displayName,
                mimeType = stored.mimeType,
                date = LocalDate.now(),
            ),
        )
    }

    /** Copies a course's prescription file into Documents as a document of its own. */
    suspend fun copyToDocuments(context: Context, prescription: PrescriptionFile, course: Course) {
        val copy = withContext(Dispatchers.IO) {
            val extension = prescription.fileName.substringAfterLast('.', "bin")
            val target = documentFile(context, "${UUID.randomUUID()}.$extension")
            target.parentFile?.mkdirs()
            runCatching { prescriptionFile(context, prescription.fileName).copyTo(target, overwrite = true) }.getOrNull()
        } ?: return
        db(context).documents().upsert(
            HealthDocument(
                // Named after the file; the course it came from is shown alongside.
                title = prescription.displayName.substringBeforeLast('.'),
                category = DocumentCategory.PRESCRIPTION,
                fileName = copy.name,
                displayName = prescription.displayName,
                mimeType = prescription.mimeType,
                date = course.startDate,
                notes = course.doctorName,
                sourceCourseId = course.id,
                sourcePrescriptionId = prescription.id,
            ),
        )
    }

    suspend fun saveDocument(context: Context, document: HealthDocument) {
        db(context).documents().upsert(document)
    }

    suspend fun deleteDocument(context: Context, document: HealthDocument) {
        db(context).documents().delete(document)
        withContext(Dispatchers.IO) { documentFile(context, document.fileName).delete() }
    }

    fun documentFile(context: Context, fileName: String): File = File(File(context.filesDir, DOCUMENTS_DIR), fileName)

    suspend fun deletePrescription(context: Context, file: PrescriptionFile) {
        db(context).prescriptionFiles().delete(file)
        deleteStoredFile(context, file.fileName)
    }

    suspend fun deleteStoredFile(context: Context, fileName: String) = withContext(Dispatchers.IO) {
        prescriptionFile(context, fileName).delete()
    }

    fun prescriptionFile(context: Context, fileName: String): File =
        File(File(context.filesDir, PRESCRIPTIONS_DIR), fileName)
}

fun Medicine.photos(): List<String> = listOfNotNull(packagePhoto, dosePhoto)
