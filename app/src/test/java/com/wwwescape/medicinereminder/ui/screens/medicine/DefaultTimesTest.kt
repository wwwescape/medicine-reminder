package com.wwwescape.medicinereminder.ui.screens.medicine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTimesTest {

    @Test
    fun `every count gives that many distinct sorted times within the day`() {
        for (count in 1..MAX_TIMES_PER_DAY) {
            val times = defaultTimes(count)
            assertEquals("count $count", count, times.size)
            assertEquals("count $count distinct", count, times.distinct().size)
            assertEquals("count $count sorted", times.sorted(), times)
            assertTrue("count $count in range", times.all { it in 0 until 24 * 60 })
        }
    }

    @Test
    fun `common counts use familiar times`() {
        assertEquals(listOf(8 * 60), defaultTimes(1))
        assertEquals(listOf(8 * 60, 20 * 60), defaultTimes(2))
        assertEquals(listOf(8 * 60, 14 * 60, 20 * 60), defaultTimes(3))
        assertEquals(listOf(8 * 60, 12 * 60, 16 * 60, 20 * 60), defaultTimes(4))
    }
}
