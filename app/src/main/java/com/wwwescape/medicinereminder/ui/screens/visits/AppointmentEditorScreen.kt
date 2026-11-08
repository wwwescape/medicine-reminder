package com.wwwescape.medicinereminder.ui.screens.visits

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.ui.components.DateDialog
import com.wwwescape.medicinereminder.ui.components.FormSection
import com.wwwescape.medicinereminder.ui.components.EditorScaffold
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.TimeDialog
import com.wwwescape.medicinereminder.util.formatDate
import com.wwwescape.medicinereminder.util.formatMinute
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The reminder offers, in minutes before the visit. */
private val REMINDER_OPTIONS = listOf(7 * 24 * 60, 24 * 60, 3 * 60, 60)

class AppointmentEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private var original: Appointment? = null
    private var loaded = false
    private var courseId: Long? = null

    var title by mutableStateOf("")
    var doctorName by mutableStateOf("")
    var place by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now().plusWeeks(1))
    var minute by mutableStateOf(10 * 60)
    var notes by mutableStateOf("")
    val reminders = mutableStateListOf(24 * 60, 3 * 60)
    var saving by mutableStateOf(false)
        private set

    val isNew get() = original == null
    val canSave get() = title.isNotBlank() && !saving

    /** A new visit starts from its course: the same doctor and clinic, on the day the course
     * ends if that's still ahead. */
    fun load(courseId: Long, appointmentId: Long, defaultTitle: String) {
        if (loaded) return
        loaded = true
        this.courseId = courseId.takeIf { it != 0L }
        viewModelScope.launch {
            val appointment = if (appointmentId != 0L) MedicineRepository.getAppointment(app, appointmentId) else null
            if (appointment != null) {
                original = appointment
                title = appointment.title
                doctorName = appointment.doctorName
                place = appointment.place
                date = appointment.date
                minute = appointment.minute
                notes = appointment.notes
                reminders.clear()
                reminders.addAll(appointment.reminders)
                return@launch
            }
            title = defaultTitle
            val course = this@AppointmentEditorViewModel.courseId?.let { MedicineRepository.getCourse(app, it) } ?: return@launch
            doctorName = course.doctorName
            place = course.clinicName
            course.endDate?.takeIf { it > LocalDate.now() }?.let { date = it }
        }
    }

    fun toggleReminder(minutes: Int) {
        if (!reminders.remove(minutes)) reminders.add(minutes)
    }

    fun current(): Appointment = (original ?: Appointment(courseId = courseId, title = "", date = date, minute = minute)).copy(
        title = title.trim(),
        doctorName = doctorName.trim(),
        place = place.trim(),
        date = date,
        minute = minute,
        notes = notes.trim(),
        reminders = reminders.sortedDescending(),
    )

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            MedicineRepository.saveAppointment(app, current())
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val appointment = original ?: return
        viewModelScope.launch {
            MedicineRepository.deleteAppointment(app, appointment)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppointmentEditorScreen(
    courseId: Long,
    appointmentId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    editor: AppointmentEditorViewModel = viewModel(key = "visit-editor-$courseId-$appointmentId"),
) {
    val context = LocalContext.current
    val defaultTitle = stringResource(R.string.visit_default_title)
    LaunchedEffect(courseId, appointmentId) { editor.load(courseId, appointmentId, defaultTitle) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        actionLabel = stringResource(if (editor.isNew) R.string.action_add_visit else R.string.action_save),
        actionEnabled = editor.canSave,
        onAction = { editor.save(onDone) },
        modifier = modifier,
    ) {
        FormSection(stringResource(R.string.section_visit)) {
            OutlinedTextField(
                value = editor.title,
                onValueChange = { editor.title = it },
                label = { Text(stringResource(R.string.field_visit_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(stringResource(R.string.field_visit_date), formatDate(editor.date), Modifier.weight(1f)) { pickingDate = true }
                PickerField(stringResource(R.string.field_visit_time), formatMinute(context, editor.minute), Modifier.weight(1f)) { pickingTime = true }
            }
        }

        FormSection(stringResource(R.string.section_visit_where)) {
            OutlinedTextField(
                value = editor.doctorName,
                onValueChange = { editor.doctorName = it },
                label = { Text(stringResource(R.string.field_doctor_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editor.place,
                onValueChange = { editor.place = it },
                label = { Text(stringResource(R.string.field_visit_place)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormSection(stringResource(R.string.section_visit_reminders)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = minutes in editor.reminders,
                        onClick = { editor.toggleReminder(minutes) },
                        label = { Text(stringResource(reminderLabel(minutes))) },
                    )
                }
            }
        }

        FormSection(stringResource(R.string.section_notes)) {
            OutlinedTextField(
                value = editor.notes,
                onValueChange = { editor.notes = it },
                placeholder = { Text(stringResource(R.string.field_visit_notes_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { addToCalendar(context, editor.current()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_add_to_calendar), modifier = Modifier.padding(start = 8.dp))
            }
            if (!editor.isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_delete_visit), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (pickingDate) {
        DateDialog(editor.date, onDismiss = { pickingDate = false }) {
            editor.date = it
            pickingDate = false
        }
    }
    if (pickingTime) {
        TimeDialog(editor.minute, onDismiss = { pickingTime = false }) {
            editor.minute = it
            pickingTime = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_visit_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    editor.delete(onDone)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PickerField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun reminderLabel(minutes: Int): Int = when (minutes) {
    7 * 24 * 60 -> R.string.visit_remind_week
    24 * 60 -> R.string.visit_remind_day
    3 * 60 -> R.string.visit_remind_3_hours
    else -> R.string.visit_remind_hour
}

/** Hands the visit to the user's calendar app, which asks before saving it. */
private fun addToCalendar(context: Context, appointment: Appointment) {
    val start = appointment.date.atTime(LocalTime.of(appointment.minute / 60, appointment.minute % 60))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, appointment.title)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000L)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, appointment.place)
        .putExtra(
            CalendarContract.Events.DESCRIPTION,
            listOf(appointment.doctorName, appointment.notes).filter { it.isNotBlank() }.joinToString("\n"),
        )
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.toast_no_calendar, Toast.LENGTH_SHORT).show()
    }
}
