package com.wwwescape.medicinereminder.data.reminders

import android.content.Context
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DoseUnit
import com.wwwescape.medicinereminder.data.db.Medicine
import com.wwwescape.medicinereminder.data.db.MedicineShape
import com.wwwescape.medicinereminder.data.schedule.ScheduledDose
import java.time.LocalDate
import java.time.LocalTime

/**
 * The made-up medicine shown by Settings → Test the reminder. It never touches the database:
 * its ids are negative, and answering it only stops the test.
 */
object TestReminder {

    fun sampleDose(context: Context): ScheduledDose {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val minute = now.hour * 60 + now.minute
        val course = Course(id = -1, name = context.getString(R.string.test_reminder_course), startDate = today, endDate = today)
        val medicine = Medicine(
            id = -1,
            courseId = course.id,
            name = context.getString(R.string.test_reminder_medicine),
            shape = MedicineShape.CAPSULE,
            color1 = 0xFF1E7FC9.toInt(),
            color2 = 0xFFF5F5F2.toInt(),
            doseAmount = 1.0,
            doseUnit = DoseUnit.CAPSULE,
            instructions = context.getString(R.string.instr_after_food),
            times = listOf(minute),
        )
        return ScheduledDose(medicine, course, today, minute, log = null)
    }
}
