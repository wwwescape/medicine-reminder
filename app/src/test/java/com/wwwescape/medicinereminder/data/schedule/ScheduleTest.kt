package com.wwwescape.medicinereminder.data.schedule

import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.DoseLog
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.db.Medicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleTest {

    private val start = LocalDate.of(2026, 11, 2) // a Monday
    private val course = Course(id = 1, name = "Eye drops", startDate = start, endDate = start.plusDays(29))
    private val courses = mapOf(1L to course)

    private fun medicine(
        id: Long = 10,
        frequency: Frequency = Frequency.DAILY,
        times: List<Int> = listOf(8 * 60, 20 * 60),
        intervalDays: Int = 1,
        weekdays: Int = 0,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        reminders: Boolean = true,
    ) = Medicine(
        id = id, courseId = 1, name = "Drops $id", color1 = 0, color2 = 0, frequency = frequency, times = times,
        intervalDays = intervalDays, weekdays = weekdays, startDate = startDate, endDate = endDate, remindersEnabled = reminders,
    )

    @Test
    fun `daily medicine follows the course dates`() {
        val m = medicine()
        assertFalse(Schedule.isScheduledOn(m, course, start.minusDays(1)))
        assertTrue(Schedule.isScheduledOn(m, course, start))
        assertTrue(Schedule.isScheduledOn(m, course, start.plusDays(29)))
        assertFalse(Schedule.isScheduledOn(m, course, start.plusDays(30)))
    }

    @Test
    fun `medicine dates override the course`() {
        val m = medicine(startDate = start.plusDays(3), endDate = start.plusDays(5))
        assertFalse(Schedule.isScheduledOn(m, course, start.plusDays(2)))
        assertTrue(Schedule.isScheduledOn(m, course, start.plusDays(4)))
        assertFalse(Schedule.isScheduledOn(m, course, start.plusDays(6)))
    }

    @Test
    fun `every other day counts from the start`() {
        val m = medicine(frequency = Frequency.EVERY_X_DAYS, intervalDays = 2)
        assertTrue(Schedule.isScheduledOn(m, course, start))
        assertFalse(Schedule.isScheduledOn(m, course, start.plusDays(1)))
        assertTrue(Schedule.isScheduledOn(m, course, start.plusDays(2)))
    }

    @Test
    fun `weekdays use Monday as bit zero`() {
        val m = medicine(frequency = Frequency.WEEKDAYS, weekdays = (1 shl 0) or (1 shl 4)) // Mon, Fri
        assertTrue(Schedule.isScheduledOn(m, course, start))
        assertFalse(Schedule.isScheduledOn(m, course, start.plusDays(1)))
        assertTrue(Schedule.isScheduledOn(m, course, start.plusDays(4)))
    }

    @Test
    fun `as needed never schedules`() {
        val m = medicine(frequency = Frequency.AS_NEEDED, times = emptyList())
        assertFalse(Schedule.isScheduledOn(m, course, start))
        assertNull(Schedule.nextReminder(start.atStartOfDay(), listOf(m), courses, emptySet()))
    }

    @Test
    fun `next reminder skips answered doses and disabled reminders`() {
        val m = medicine()
        val quiet = medicine(id = 11, times = listOf(7 * 60), reminders = false)
        val morning = DoseKey(m.id, start, 8 * 60)
        assertEquals(
            start.atTime(8, 0),
            Schedule.nextReminder(start.atStartOfDay(), listOf(m, quiet), courses, emptySet()),
        )
        assertEquals(
            start.atTime(20, 0),
            Schedule.nextReminder(start.atStartOfDay(), listOf(m, quiet), courses, setOf(morning)),
        )
    }

    @Test
    fun `next reminder rolls over to the next day and stops after the course`() {
        val m = medicine()
        assertEquals(start.plusDays(1).atTime(8, 0), Schedule.nextReminder(start.atTime(20, 0), listOf(m), courses, emptySet()))
        assertNull(Schedule.nextReminder(start.plusDays(29).atTime(21, 0), listOf(m), courses, emptySet()))
    }

    @Test
    fun `unanswered returns only the slot's open doses`() {
        val a = medicine(id = 10)
        val b = medicine(id = 12)
        val logs = mapOf(DoseKey(10, start, 480) to DoseLog(10, start, 480, DoseStatus.TAKEN, 1.0))
        val open = Schedule.unansweredAt(start, 480, listOf(a, b), courses, logs)
        assertEquals(listOf(12L), open.map { it.medicine.id })
    }

    @Test
    fun `adherence counts only doses that are due`() {
        val m = medicine()
        val logs = mapOf(
            DoseKey(10, start, 480) to DoseLog(10, start, 480, DoseStatus.TAKEN, 1.0),
            DoseKey(10, start, 1200) to DoseLog(10, start, 1200, DoseStatus.SKIPPED, 1.0),
        )
        val result = adherence(LocalDateTime.of(start.plusDays(1), java.time.LocalTime.of(9, 0)), listOf(m), courses, logs, start)
        assertEquals(3, result.due)
        assertEquals(1, result.taken)
        assertEquals(1, result.skipped)
        assertEquals(1, result.missed)
    }
}
