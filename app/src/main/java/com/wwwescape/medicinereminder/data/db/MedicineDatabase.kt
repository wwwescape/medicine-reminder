package com.wwwescape.medicinereminder.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class Converters {
    @TypeConverter fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)
    @TypeConverter fun minutesToString(minutes: List<Int>): String = minutes.joinToString(",")
    @TypeConverter fun stringToMinutes(value: String): List<Int> =
        value.split(',').mapNotNull { it.trim().toIntOrNull() }
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY startDate DESC, id DESC")
    fun observeAll(): Flow<List<Course>>

    @Query("SELECT * FROM courses")
    suspend fun getAll(): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun observe(id: Long): Flow<Course?>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun get(id: Long): Course?

    @Insert suspend fun insert(course: Course): Long
    @Update suspend fun update(course: Course)
    @Delete suspend fun delete(course: Course)
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines")
    suspend fun getAll(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE courseId = :courseId ORDER BY name COLLATE NOCASE")
    fun observeForCourse(courseId: Long): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: Long): Medicine?

    @Upsert suspend fun upsert(medicine: Medicine): Long
    @Delete suspend fun delete(medicine: Medicine)
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_log WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log WHERE date BETWEEN :from AND :to")
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<DoseLog>

    @Query("SELECT dose_log.* FROM dose_log JOIN medicines ON medicines.id = dose_log.medicineId WHERE medicines.courseId = :courseId")
    fun observeForCourse(courseId: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log")
    fun observeAll(): Flow<List<DoseLog>>

    @Upsert suspend fun upsert(log: DoseLog)

    @Query("DELETE FROM dose_log WHERE medicineId = :medicineId AND date = :date AND minute = :minute")
    suspend fun delete(medicineId: Long, date: LocalDate, minute: Int)
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY date, minute")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments")
    suspend fun getAll(): List<Appointment>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun get(id: Long): Appointment?

    @Upsert suspend fun upsert(appointment: Appointment): Long
    @Delete suspend fun delete(appointment: Appointment)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<HealthDocument>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): HealthDocument?

    @Upsert suspend fun upsert(document: HealthDocument): Long
    @Delete suspend fun delete(document: HealthDocument)
}

/** Whole-database reads and the all-or-nothing replace behind backup and restore. */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM courses") abstract suspend fun courses(): List<Course>
    @Query("SELECT * FROM medicines") abstract suspend fun medicines(): List<Medicine>
    @Query("SELECT * FROM prescription_files") abstract suspend fun prescriptionFiles(): List<PrescriptionFile>
    @Query("SELECT * FROM dose_log") abstract suspend fun doseLogs(): List<DoseLog>
    @Query("SELECT * FROM appointments") abstract suspend fun appointments(): List<Appointment>
    @Query("SELECT * FROM documents") abstract suspend fun documents(): List<HealthDocument>

    @Insert protected abstract suspend fun insertCourses(items: List<Course>)
    @Insert protected abstract suspend fun insertMedicines(items: List<Medicine>)
    @Insert protected abstract suspend fun insertPrescriptionFiles(items: List<PrescriptionFile>)
    @Insert protected abstract suspend fun insertDoseLogs(items: List<DoseLog>)
    @Insert protected abstract suspend fun insertAppointments(items: List<Appointment>)
    @Insert protected abstract suspend fun insertDocuments(items: List<HealthDocument>)

    @Query("DELETE FROM dose_log") protected abstract suspend fun clearDoseLogs()
    @Query("DELETE FROM documents") protected abstract suspend fun clearDocuments()
    @Query("DELETE FROM appointments") protected abstract suspend fun clearAppointments()
    @Query("DELETE FROM prescription_files") protected abstract suspend fun clearPrescriptionFiles()
    @Query("DELETE FROM medicines") protected abstract suspend fun clearMedicines()
    @Query("DELETE FROM courses") protected abstract suspend fun clearCourses()

    /** Replaces every row with [courses]… in one transaction: either all of it lands, or the
     * database is left exactly as it was. Ids are kept, so the rows still point at each other. */
    @Transaction
    open suspend fun replaceAll(
        courses: List<Course>,
        medicines: List<Medicine>,
        prescriptionFiles: List<PrescriptionFile>,
        doseLogs: List<DoseLog>,
        appointments: List<Appointment>,
        documents: List<HealthDocument>,
    ) {
        clearDoseLogs()
        clearDocuments()
        clearAppointments()
        clearPrescriptionFiles()
        clearMedicines()
        clearCourses()
        insertCourses(courses)
        insertMedicines(medicines)
        insertPrescriptionFiles(prescriptionFiles)
        insertAppointments(appointments)
        insertDocuments(documents)
        insertDoseLogs(doseLogs)
    }
}

@Dao
interface PrescriptionFileDao {
    @Query("SELECT * FROM prescription_files WHERE courseId = :courseId ORDER BY addedAt")
    fun observeForCourse(courseId: Long): Flow<List<PrescriptionFile>>

    @Query("SELECT * FROM prescription_files WHERE courseId = :courseId")
    suspend fun getForCourse(courseId: Long): List<PrescriptionFile>

    @Insert suspend fun insert(file: PrescriptionFile): Long
    @Delete suspend fun delete(file: PrescriptionFile)
}

@Database(
    entities = [Course::class, PrescriptionFile::class, Medicine::class, DoseLog::class, Appointment::class, HealthDocument::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // Course doctor and clinic details, medicine photos, and follow-up visits.
        AutoMigration(from = 1, to = 2),
        // The document registry.
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class MedicineDatabase : RoomDatabase() {
    abstract fun courses(): CourseDao
    abstract fun medicines(): MedicineDao
    abstract fun doseLog(): DoseLogDao
    abstract fun prescriptionFiles(): PrescriptionFileDao
    abstract fun appointments(): AppointmentDao
    abstract fun documents(): DocumentDao
    abstract fun backup(): BackupDao

    companion object {
        const val NAME = "medicine_reminder.db"

        @Volatile private var instance: MedicineDatabase? = null

        fun get(context: Context): MedicineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MedicineDatabase::class.java, NAME)
                .build()
                .also { instance = it }
        }
    }
}
