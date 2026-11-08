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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BackupFormatTest {

    private val start = LocalDate.of(2026, 11, 2)

    private val data = BackupData(
        courses = listOf(
            Course(
                id = 4, name = "Eye drops", condition = "Dry eyes", doctorName = "Dr. Rao", doctorSpecialty = "Ophthalmologist",
                doctorPhone = "+91 98765 43210", clinicName = "City Eye Hospital", clinicAddress = "MG Road", clinicPhone = "080 2555",
                patientId = "CEH-1", notes = "Notes", startDate = start, endDate = start.plusDays(29), createdAt = 1,
            ),
            Course(id = 5, name = "Ongoing", startDate = start, endDate = null, createdAt = 2),
        ),
        medicines = listOf(
            Medicine(
                id = 7, courseId = 4, name = "Refresh Tears", strength = "0.5%", shape = MedicineShape.DROPS, color1 = -1, color2 = 0xFF19A99A.toInt(),
                doseAmount = 1.5, doseUnit = DoseUnit.DROP, instructions = "Both eyes", notes = "n", frequency = Frequency.WEEKDAYS,
                intervalDays = 2, weekdays = 0b1010101, times = listOf(480, 840, 1200), startDate = start.plusDays(1), endDate = start.plusDays(9),
                remindersEnabled = false, packagePhoto = "a.jpg", dosePhoto = null, createdAt = 3,
            ),
        ),
        prescriptionFiles = listOf(PrescriptionFile(id = 9, courseId = 4, fileName = "rx.pdf", displayName = "Rx.pdf", mimeType = "application/pdf", addedAt = 4)),
        doseLogs = listOf(DoseLog(medicineId = 7, date = start, minute = 480, status = DoseStatus.SKIPPED, amount = 1.5, loggedAt = 5)),
        appointments = listOf(
            Appointment(id = 11, courseId = 4, title = "Follow-up", doctorName = "Dr. Rao", place = "City Eye", date = start.plusDays(8), minute = 630, notes = "Fasting", reminders = listOf(1440, 60), createdAt = 6),
            Appointment(id = 12, courseId = null, title = "Dentist", date = start, minute = 600, reminders = emptyList(), createdAt = 7),
        ),
        documents = listOf(
            HealthDocument(
                id = 13, title = "Glasses", category = DocumentCategory.GLASSES, fileName = "g.pdf", displayName = "g.pdf", mimeType = "application/pdf",
                date = start, notes = "-1.25", sourceCourseId = 4, sourcePrescriptionId = 9, addedAt = 8,
            ),
        ),
        settings = mapOf("theme_mode" to "DARK", "full_screen" to true, "count" to 3, "big" to 5_000_000_000L, "ratio" to 0.5f, "tags" to setOf("a", "b")),
    )

    @Test
    fun `everything survives a round trip through JSON text`() {
        val text = BackupFormat.toJson(data).toString(2)
        assertEquals(data, BackupFormat.fromJson(JSONObject(text)))
    }

    @Test
    fun `missing fields and unknown names fall back to defaults`() {
        val old = JSONObject(
            """
            {
              "courses": [{ "id": 1, "name": "Old", "startDate": "2026-01-01" }],
              "medicines": [{ "id": 2, "courseId": 1, "name": "Pill", "color1": 1, "color2": 2, "shape": "SOMETHING_NEW" }]
            }
            """.trimIndent(),
        )
        val read = BackupFormat.fromJson(old)
        assertEquals("", read.courses.single().doctorName)
        assertNull(read.courses.single().endDate)
        assertEquals(MedicineShape.ROUND, read.medicines.single().shape)
        assertEquals(emptyList<Int>(), read.medicines.single().times)
        assertEquals(emptyList<DoseLog>(), read.doseLogs)
    }
}
