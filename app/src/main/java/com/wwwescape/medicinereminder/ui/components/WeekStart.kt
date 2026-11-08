package com.wwwescape.medicinereminder.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import com.wwwescape.medicinereminder.data.settings.WeekStart
import java.time.DayOfWeek

/** The first day of the week, from Settings (the region's own until the user picks). */
@Composable
fun rememberWeekStart(): DayOfWeek {
    val context = LocalContext.current
    val settings by remember { SettingsRepository.settingsFlow(context.applicationContext) }.collectAsState(initial = null)
    return (settings?.weekStart ?: WeekStart.forLocale()).day
}

/** The seven days of the week in display order, starting from [first]. */
fun weekDays(first: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { first.plus(it) }
