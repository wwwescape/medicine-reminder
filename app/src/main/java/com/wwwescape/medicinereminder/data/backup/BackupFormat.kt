package com.wwwescape.medicinereminder.data.backup

import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DocumentCategory
import com.wwwescape.medicinereminder.data.db.DoseLog
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.DoseUnit
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.db.HealthDocument
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.MedicineShape
import com.wwwescape.medicinereminder.data.db.PrescriptionFile
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Everything a backup holds besides the files themselves. */
data class BackupData(
    val courses: List<Course> = emptyList(),
    val medicines: List<Medicine> = emptyList(),
    val prescriptionFiles: List<PrescriptionFile> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
    val appointments: List<Appointment> = emptyList(),
    val documents: List<HealthDocument> = emptyList(),
    /** App settings by preference key: Boolean, String, Int, Long, Float or Set<String>. */
    val settings: Map<String, Any> = emptyMap(),
)

/**
 * The backup's `data.json`. Every field is written by name, and reading tolerates missing
 * fields and unknown enum names (using each field's default), so a backup made by an older or
 * newer version of the app still imports. Bump [FORMAT_VERSION] only for changes that older
 * readers can't make sense of.
 */
object BackupFormat {

    const val FORMAT_VERSION = 1

    fun toJson(data: BackupData): JSONObject = JSONObject()
        .put("courses", JSONArray(data.courses.map(::course)))
        .put("medicines", JSONArray(data.medicines.map(::medicine)))
        .put("prescriptionFiles", JSONArray(data.prescriptionFiles.map(::prescriptionFile)))
        .put("doseLog", JSONArray(data.doseLogs.map(::doseLog)))
        .put("appointments", JSONArray(data.appointments.map(::appointment)))
        .put("documents", JSONArray(data.documents.map(::document)))
        .put("settings", settings(data.settings))

    fun fromJson(json: JSONObject): BackupData = BackupData(
        courses = json.objects("courses").map(::course),
        medicines = json.objects("medicines").map(::medicine),
        prescriptionFiles = json.objects("prescriptionFiles").map(::prescriptionFile),
        doseLogs = json.objects("doseLog").map(::doseLog),
        appointments = json.objects("appointments").map(::appointment),
        documents = json.objects("documents").map(::document),
        settings = json.optJSONObject("settings")?.let(::settings) ?: emptyMap(),
    )

    private fun course(c: Course) = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("condition", c.condition)
        .put("doctorName", c.doctorName)
        .put("doctorSpecialty", c.doctorSpecialty)
        .put("doctorPhone", c.doctorPhone)
        .put("clinicName", c.clinicName)
        .put("clinicAddress", c.clinicAddress)
        .put("clinicPhone", c.clinicPhone)
        .put("patientId", c.patientId)
        .put("notes", c.notes)
        .put("startDate", c.startDate.toString())
        .putOpt("endDate", c.endDate?.toString())
        .put("createdAt", c.createdAt)

    private fun course(o: JSONObject) = Course(
        id = o.getLong("id"),
        name = o.getString("name"),
        condition = o.optString("condition"),
        doctorName = o.optString("doctorName"),
        doctorSpecialty = o.optString("doctorSpecialty"),
        doctorPhone = o.optString("doctorPhone"),
        clinicName = o.optString("clinicName"),
        clinicAddress = o.optString("clinicAddress"),
        clinicPhone = o.optString("clinicPhone"),
        patientId = o.optString("patientId"),
        notes = o.optString("notes"),
        startDate = LocalDate.parse(o.getString("startDate")),
        endDate = o.optDate("endDate"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun medicine(m: Medicine) = JSONObject()
        .put("id", m.id)
        .put("courseId", m.courseId)
        .put("name", m.name)
        .put("strength", m.strength)
        .put("shape", m.shape.name)
        .put("color1", m.color1)
        .put("color2", m.color2)
        .put("doseAmount", m.doseAmount)
        .put("doseUnit", m.doseUnit.name)
        .put("instructions", m.instructions)
        .put("notes", m.notes)
        .put("frequency", m.frequency.name)
        .put("intervalDays", m.intervalDays)
        .put("weekdays", m.weekdays)
        .put("times", JSONArray(m.times))
        .putOpt("startDate", m.startDate?.toString())
        .putOpt("endDate", m.endDate?.toString())
        .put("remindersEnabled", m.remindersEnabled)
        .putOpt("packagePhoto", m.packagePhoto)
        .putOpt("dosePhoto", m.dosePhoto)
        .put("createdAt", m.createdAt)

    private fun medicine(o: JSONObject) = Medicine(
        id = o.getLong("id"),
        courseId = o.getLong("courseId"),
        name = o.getString("name"),
        strength = o.optString("strength"),
        shape = o.optEnum("shape", MedicineShape.ROUND),
        color1 = o.getInt("color1"),
        color2 = o.getInt("color2"),
        doseAmount = o.optDouble("doseAmount", 1.0),
        doseUnit = o.optEnum("doseUnit", DoseUnit.TABLET),
        instructions = o.optString("instructions"),
        notes = o.optString("notes"),
        frequency = o.optEnum("frequency", Frequency.DAILY),
        intervalDays = o.optInt("intervalDays", 1),
        weekdays = o.optInt("weekdays", 0),
        times = o.optJSONArray("times")?.let { a -> (0 until a.length()).map(a::getInt) } ?: emptyList(),
        startDate = o.optDate("startDate"),
        endDate = o.optDate("endDate"),
        remindersEnabled = o.optBoolean("remindersEnabled", true),
        packagePhoto = o.optStringOrNull("packagePhoto"),
        dosePhoto = o.optStringOrNull("dosePhoto"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun prescriptionFile(f: PrescriptionFile) = JSONObject()
        .put("id", f.id)
        .put("courseId", f.courseId)
        .put("fileName", f.fileName)
        .put("displayName", f.displayName)
        .put("mimeType", f.mimeType)
        .put("addedAt", f.addedAt)

    private fun prescriptionFile(o: JSONObject) = PrescriptionFile(
        id = o.getLong("id"),
        courseId = o.getLong("courseId"),
        fileName = o.getString("fileName"),
        displayName = o.optString("displayName", o.getString("fileName")),
        mimeType = o.optString("mimeType", "application/octet-stream"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
    )

    private fun doseLog(l: DoseLog) = JSONObject()
        .put("medicineId", l.medicineId)
        .put("date", l.date.toString())
        .put("minute", l.minute)
        .put("status", l.status.name)
        .put("amount", l.amount)
        .put("loggedAt", l.loggedAt)

    private fun doseLog(o: JSONObject) = DoseLog(
        medicineId = o.getLong("medicineId"),
        date = LocalDate.parse(o.getString("date")),
        minute = o.getInt("minute"),
        status = o.optEnum("status", DoseStatus.TAKEN),
        amount = o.optDouble("amount", 1.0),
        loggedAt = o.optLong("loggedAt", System.currentTimeMillis()),
    )

    private fun appointment(a: Appointment) = JSONObject()
        .put("id", a.id)
        .putOpt("courseId", a.courseId)
        .put("title", a.title)
        .put("doctorName", a.doctorName)
        .put("place", a.place)
        .put("date", a.date.toString())
        .put("minute", a.minute)
        .put("notes", a.notes)
        .put("reminders", JSONArray(a.reminders))
        .put("createdAt", a.createdAt)

    private fun appointment(o: JSONObject) = Appointment(
        id = o.getLong("id"),
        courseId = o.optLongOrNull("courseId"),
        title = o.getString("title"),
        doctorName = o.optString("doctorName"),
        place = o.optString("place"),
        date = LocalDate.parse(o.getString("date")),
        minute = o.getInt("minute"),
        notes = o.optString("notes"),
        reminders = o.optJSONArray("reminders")?.let { a -> (0 until a.length()).map(a::getInt) } ?: emptyList(),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun document(d: HealthDocument) = JSONObject()
        .put("id", d.id)
        .put("title", d.title)
        .put("category", d.category.name)
        .put("fileName", d.fileName)
        .put("displayName", d.displayName)
        .put("mimeType", d.mimeType)
        .put("date", d.date.toString())
        .put("notes", d.notes)
        .putOpt("sourceCourseId", d.sourceCourseId)
        .putOpt("sourcePrescriptionId", d.sourcePrescriptionId)
        .put("addedAt", d.addedAt)

    private fun document(o: JSONObject) = HealthDocument(
        id = o.getLong("id"),
        title = o.getString("title"),
        category = o.optEnum("category", DocumentCategory.OTHER),
        fileName = o.getString("fileName"),
        displayName = o.optString("displayName", o.getString("fileName")),
        mimeType = o.optString("mimeType", "application/octet-stream"),
        date = LocalDate.parse(o.getString("date")),
        notes = o.optString("notes"),
        sourceCourseId = o.optLongOrNull("sourceCourseId"),
        sourcePrescriptionId = o.optLongOrNull("sourcePrescriptionId"),
        addedAt = o.optLong("addedAt", System.currentTimeMillis()),
    )

    /** Each setting as {"type": …, "value": …}, so its preference type survives the trip. */
    private fun settings(values: Map<String, Any>): JSONObject = JSONObject().apply {
        values.forEach { (key, value) ->
            val (type, json) = when (value) {
                is Boolean -> "boolean" to value
                is Int -> "int" to value
                is Long -> "long" to value
                is Float -> "float" to value.toDouble()
                is String -> "string" to value
                is Set<*> -> "stringSet" to JSONArray(value.map { it.toString() })
                else -> return@forEach
            }
            put(key, JSONObject().put("type", type).put("value", json))
        }
    }

    private fun settings(json: JSONObject): Map<String, Any> = buildMap {
        json.keys().forEach { key ->
            val entry = json.optJSONObject(key) ?: return@forEach
            val value: Any = when (entry.optString("type")) {
                "boolean" -> entry.getBoolean("value")
                "int" -> entry.getInt("value")
                "long" -> entry.getLong("value")
                "float" -> entry.getDouble("value").toFloat()
                "string" -> entry.getString("value")
                "stringSet" -> entry.getJSONArray("value").let { a -> (0 until a.length()).map(a::getString).toSet() }
                else -> return@forEach
            }
            put(key, value)
        }
    }

    private fun JSONObject.objects(key: String): List<JSONObject> =
        optJSONArray(key)?.let { a -> (0 until a.length()).map(a::getJSONObject) } ?: emptyList()

    private fun JSONObject.optDate(key: String): LocalDate? = optStringOrNull(key)?.let(LocalDate::parse)

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, default: T): T =
        optString(key).let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default
}
