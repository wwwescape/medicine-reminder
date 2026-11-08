package com.wwwescape.medicinereminder.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.Snapshot
import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.reminders.dateTime
import com.wwwescape.medicinereminder.data.schedule.Schedule
import com.wwwescape.medicinereminder.ui.MedicineViewModel
import com.wwwescape.medicinereminder.ui.components.AppointmentRow
import com.wwwescape.medicinereminder.ui.components.DateDialog
import com.wwwescape.medicinereminder.ui.components.DoseRow
import com.wwwescape.medicinereminder.ui.components.DoseState
import com.wwwescape.medicinereminder.ui.components.EmptyStateNotice
import com.wwwescape.medicinereminder.ui.components.PermissionPrompts
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.rememberNow
import com.wwwescape.medicinereminder.ui.components.rememberWeekStart
import com.wwwescape.medicinereminder.ui.components.state
import com.wwwescape.medicinereminder.ui.icons.MedicineIcon
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatDate
import com.wwwescape.medicinereminder.util.formatMinute
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** How far ahead the Today screen lists doctor's visits. */
private const val UPCOMING_VISIT_DAYS = 14L

/** How a whole day went, for the dots on the week strip. */
enum class DayStatus { NONE, UPCOMING, DONE, PARTIAL, MISSED }

fun dayStatus(snapshot: Snapshot, date: LocalDate, now: LocalDateTime): DayStatus {
    val doses = Schedule.dosesOn(date, snapshot.medicines, snapshot.courses, snapshot.logs)
    if (doses.isEmpty()) return DayStatus.NONE
    val states = doses.map { it.state(now) }
    val answered = states.count { it == DoseState.TAKEN || it == DoseState.SKIPPED }
    return when {
        answered == states.size -> DayStatus.DONE
        states.any { it == DoseState.MISSED } -> if (answered == 0) DayStatus.MISSED else DayStatus.PARTIAL
        else -> DayStatus.UPCOMING
    }
}

@Composable
fun DayStatus.color(): Color = when (this) {
    DayStatus.NONE -> Color.Transparent
    DayStatus.UPCOMING -> MaterialTheme.colorScheme.outline
    DayStatus.DONE -> MaterialTheme.colorScheme.primary
    DayStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
    DayStatus.MISSED -> MaterialTheme.colorScheme.error
}

@Composable
fun TodayScreen(
    viewModel: MedicineViewModel,
    onNewCourse: () -> Unit,
    onOpenVisit: (Appointment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val now = rememberNow()
    val weekStart = rememberWeekStart()
    val today = now.toLocalDate()
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // A new day starts at its top; coming back to the screen keeps the scroll position.
    var shownDay by rememberSaveable { mutableStateOf(selectedEpochDay) }
    LaunchedEffect(selectedEpochDay) {
        if (shownDay != selectedEpochDay) {
            shownDay = selectedEpochDay
            listState.scrollToItem(0)
        }
    }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val doses = remember(snapshot, selected) { Schedule.dosesOn(selected, snapshot.medicines, snapshot.courses, snapshot.logs) }
    val asNeeded = remember(snapshot, selected) {
        snapshot.medicines.filter { medicine ->
            medicine.frequency == Frequency.AS_NEEDED &&
                snapshot.courses[medicine.courseId]?.let { Schedule.isActiveOn(medicine, it, selected) } == true
        }
    }

    ReadableWidth(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pinned while the day's doses scroll underneath, so any day stays a tap away.
            stickyHeader(key = "week") {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        MonthBar(
                            selected = selected,
                            today = today,
                            onPickDate = { pickingDate = true },
                            onBackToToday = { selectedEpochDay = today.toEpochDay() },
                        )
                        WeekStrip(
                            selected = selected,
                            today = today,
                            weekStart = weekStart,
                            status = { dayStatus(snapshot, it, now) },
                            onSelect = { selectedEpochDay = it.toEpochDay() },
                        )
                    }
                }
            }
            item { PermissionPrompts() }
            val comingUp = snapshot.appointments.filter { it.dateTime >= now && it.date <= today.plusDays(UPCOMING_VISIT_DAYS) }
            if (selected == today && comingUp.isNotEmpty()) {
                item(key = "visits") {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Text(
                            stringResource(R.string.today_coming_up),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(start = 16.dp, top = 14.dp),
                        )
                        comingUp.forEach { visit -> AppointmentRow(visit, now, onClick = { onOpenVisit(visit) }) }
                    }
                }
            }
            item {
                DayHeading(
                    date = selected,
                    today = today,
                    taken = doses.count { it.log?.status == DoseStatus.TAKEN },
                    total = doses.size,
                )
            }
            when {
                !snapshot.loaded -> Unit
                snapshot.courses.isEmpty() -> item {
                    FirstCourseCard(onNewCourse)
                }
                doses.isEmpty() && asNeeded.isEmpty() -> item {
                    EmptyStateNotice(
                        title = stringResource(R.string.today_nothing_title),
                        body = stringResource(R.string.today_nothing_body),
                        icon = Icons.Rounded.EventAvailable,
                    )
                }
            }
            doses.groupBy { it.minute }.forEach { (minute, slot) ->
                item(key = "time-$minute") {
                    Text(
                        text = formatMinute(LocalContext.current, minute),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(slot, key = { "dose-${it.key}" }) { dose ->
                    DoseRow(
                        dose = dose,
                        now = now,
                        onTake = viewModel::take,
                        onSkip = viewModel::skip,
                        onUndo = viewModel::undo,
                        showCourse = snapshot.courses.size > 1,
                    )
                }
            }
            if (asNeeded.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.today_as_needed)) }
                items(asNeeded, key = { "prn-${it.id}" }) { medicine ->
                    val takenToday = snapshot.logs.values.count { it.medicineId == medicine.id && it.date == selected && it.status == DoseStatus.TAKEN }
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MedicineIcon(medicine, width = 52.dp)
                            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    doseText(LocalContext.current, medicine),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (takenToday > 0) {
                                    Text(
                                        pluralStringResource(R.plurals.as_needed_taken, takenToday, takenToday),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (selected == today) {
                                FilledTonalButton(onClick = { viewModel.takeAsNeeded(medicine) }) { Text(stringResource(R.string.action_log_dose)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        DateDialog(selected, onDismiss = { pickingDate = false }) {
            selectedEpochDay = it.toEpochDay()
            pickingDate = false
        }
    }
}

/** The month on show, which opens a calendar to jump to any date, and a way back to today. */
@Composable
private fun MonthBar(selected: LocalDate, today: LocalDate, onPickDate: () -> Unit, onBackToToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onPickDate) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                selected.format(DateTimeFormatter.ofPattern(if (selected.year == today.year) "MMMM" else "MMMM yyyy", LocalConfiguration.current.locales[0])),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.action_pick_date))
        }
        Spacer(Modifier.weight(1f))
        if (selected != today) {
            TextButton(onClick = onBackToToday) { Text(stringResource(R.string.action_back_to_today)) }
        }
    }
}

/**
 * The selected day's week. Every day takes an equal share of the width and its circle scales to
 * fit (up to 40dp), so seven days and both arrows fit on narrow phones and at large font and
 * display sizes without wrapping.
 */
@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    weekStart: DayOfWeek,
    status: (LocalDate) -> DayStatus,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDay = selected.with(TemporalAdjusters.previousOrSame(weekStart))
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onSelect(selected.minusWeeks(1)) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.action_previous_week))
        }
        Row(modifier = Modifier.weight(1f)) {
            (0L until 7L).map { firstDay.plusDays(it) }.forEach { date ->
                val isSelected = date == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelect(date) }
                        .padding(vertical = 6.dp, horizontal = 1.dp),
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .widthIn(max = 40.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    date == today -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                },
                            ),
                    ) {
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                date == today -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(status(date).color()),
                    )
                }
            }
        }
        IconButton(onClick = { onSelect(selected.plusWeeks(1)) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.action_next_week))
        }
    }
}

@Composable
private fun DayHeading(date: LocalDate, today: LocalDate, taken: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (date) {
                    today -> stringResource(R.string.today)
                    today.minusDays(1) -> stringResource(R.string.yesterday)
                    today.plusDays(1) -> stringResource(R.string.tomorrow)
                    else -> formatDate(date)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (total > 0) {
                Text(
                    stringResource(R.string.today_taken_of, taken, total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FirstCourseCard(onNewCourse: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.MedicalServices, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.first_course_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.first_course_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
            Button(onClick = onNewCourse, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.action_new_course)) }
        }
    }
}
