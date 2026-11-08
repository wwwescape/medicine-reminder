package com.wwwescape.medicinereminder.ui.screens.courses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.db.PrescriptionFile
import com.wwwescape.medicinereminder.data.reminders.dateTime
import com.wwwescape.medicinereminder.data.schedule.Adherence
import com.wwwescape.medicinereminder.data.schedule.Schedule
import com.wwwescape.medicinereminder.data.schedule.adherence
import com.wwwescape.medicinereminder.ui.MedicineViewModel
import com.wwwescape.medicinereminder.ui.components.AppointmentRow
import com.wwwescape.medicinereminder.ui.components.CareDetailsCard
import com.wwwescape.medicinereminder.ui.components.DoseRow
import com.wwwescape.medicinereminder.ui.components.hasCareDetails
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.openPrescription
import com.wwwescape.medicinereminder.ui.components.PrescriptionThumbnail
import com.wwwescape.medicinereminder.ui.components.rememberNow
import com.wwwescape.medicinereminder.ui.icons.MedicineIcon
import com.wwwescape.medicinereminder.ui.screens.today.DayStatus
import com.wwwescape.medicinereminder.ui.screens.today.color
import com.wwwescape.medicinereminder.ui.screens.today.dayStatus
import com.wwwescape.medicinereminder.util.courseProgressText
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatDate
import com.wwwescape.medicinereminder.util.formatShortDate
import com.wwwescape.medicinereminder.util.scheduleText
import java.time.LocalDate

/** The most days the history grid shows; longer courses show their latest days. */
private const val HISTORY_DAYS = 120L

val PRESCRIPTION_MIME_TYPES = arrayOf("image/*", "application/pdf")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseDetailScreen(
    courseId: Long,
    viewModel: MedicineViewModel,
    onEditCourse: () -> Unit,
    onAddMedicine: () -> Unit,
    onEditMedicine: (Long) -> Unit,
    onAddVisit: () -> Unit,
    onEditVisit: (Long) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val files by remember(courseId) { viewModel.prescriptions(courseId) }.collectAsStateWithLifecycle(emptyList())
    val course = snapshot.courses[courseId] ?: return
    val medicines = snapshot.medicines.filter { it.courseId == courseId }
    val now = rememberNow()
    val today = now.toLocalDate()
    val result = remember(snapshot, courseId, now) { adherence(now, medicines, snapshot.courses, snapshot.logs, course.startDate) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fileToRemove by remember { mutableStateOf<PrescriptionFile?>(null) }
    var fileMenu by remember { mutableStateOf<Long?>(null) }
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val saveToDocuments by viewModel.saveToDocuments.collectAsStateWithLifecycle()
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addPrescriptions(courseId, uris)
    }

    ReadableWidth(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(course.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        listOf(courseProgressText(context, course, today), course.condition).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        course.endDate?.let { stringResource(R.string.course_dates, formatDate(course.startDate), formatDate(it)) }
                            ?: stringResource(R.string.course_dates_ongoing, formatDate(course.startDate)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    courseFraction(course, today)?.let { fraction ->
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                    AdherenceRow(result, modifier = Modifier.padding(top = 16.dp))
                    if (course.notes.isNotBlank()) {
                        Text(
                            course.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    FilledTonalButton(onClick = onEditCourse, modifier = Modifier.padding(top = 16.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_edit_course), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (course.hasCareDetails()) {
                SectionLabel(stringResource(R.string.section_doctor))
                CareDetailsCard(course)
            }

            SectionLabel(stringResource(R.string.section_medicines))
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                medicines.forEach { medicine ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditMedicine(medicine.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        MedicineIcon(medicine, width = 52.dp)
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(doseText(context, medicine), medicine.instructions).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(scheduleText(context, medicine), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            val start = Schedule.startOf(medicine, course)
                            val end = Schedule.endOf(medicine, course)
                            if (start != course.startDate || end != course.endDate) {
                                Text(
                                    end?.let { stringResource(R.string.course_dates, formatShortDate(start), formatShortDate(it)) }
                                        ?: stringResource(R.string.course_dates_ongoing, formatShortDate(start)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                TextButton(onClick = onAddMedicine, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.action_add_medicine), modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionLabel(stringResource(R.string.section_visits))
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                // Upcoming visits first (soonest at the top), then past ones, latest first.
                val visits = snapshot.appointments.filter { it.courseId == courseId }
                    .partition { it.dateTime >= now }
                    .let { (upcoming, past) -> upcoming.sortedBy { it.dateTime } + past.sortedByDescending { it.dateTime } }
                visits.forEach { visit ->
                    AppointmentRow(visit, now, onClick = { onEditVisit(visit.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                TextButton(onClick = onAddVisit, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.action_add_visit), modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionLabel(stringResource(R.string.section_prescriptions))
            SaveToDocumentsSwitch(checked = saveToDocuments, onCheckedChange = viewModel::setSaveToDocuments)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                files.forEach { file ->
                    val inDocuments = documents.any { it.sourcePrescriptionId == file.id }
                    Box {
                        PrescriptionThumbnail(
                            file = MedicineRepository.prescriptionFile(context, file.fileName),
                            displayName = file.displayName,
                            mimeType = file.mimeType,
                            onClick = { openPrescription(context, file.fileName, file.mimeType, file.displayName) },
                            onLongClick = { fileMenu = file.id },
                        )
                        DropdownMenu(expanded = fileMenu == file.id, onDismissRequest = { fileMenu = null }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (inDocuments) R.string.file_in_documents else R.string.action_save_to_documents)) },
                                leadingIcon = { Icon(if (inDocuments) Icons.Rounded.Check else Icons.Rounded.Folder, contentDescription = null) },
                                enabled = !inDocuments,
                                onClick = {
                                    fileMenu = null
                                    viewModel.copyToDocuments(file, course)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_remove)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    fileMenu = null
                                    fileToRemove = file
                                },
                            )
                        }
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(MaterialTheme.shapes.large)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                        .clickable { picker.launch(PRESCRIPTION_MIME_TYPES) },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.action_add_file), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (files.isNotEmpty()) {
                Text(stringResource(R.string.prescriptions_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (medicines.isNotEmpty()) {
                SectionLabel(stringResource(R.string.section_history))
                val last = minOf(course.endDate ?: today, maxOf(today, course.startDate))
                val first = maxOf(course.startDate, last.minusDays(HISTORY_DAYS - 1))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    var date = first
                    while (date <= last) {
                        val day = date
                        HistoryDay(
                            date = day,
                            status = dayStatus(snapshot.copy(medicines = medicines), day, now),
                            isToday = day == today,
                            onClick = { openDay = day.toEpochDay() },
                        )
                        date = date.plusDays(1)
                    }
                }
                Text(stringResource(R.string.history_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_delete_course), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    openDay?.let { epochDay ->
        DaySheet(
            date = LocalDate.ofEpochDay(epochDay),
            course = course,
            viewModel = viewModel,
            onDismiss = { openDay = null },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_course_title)) },
            text = { Text(stringResource(R.string.delete_course_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteCourse(course)
                    onDeleted()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    fileToRemove?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToRemove = null },
            title = { Text(stringResource(R.string.remove_file_title)) },
            text = { Text(file.displayName) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePrescription(file)
                    fileToRemove = null
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { fileToRemove = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AdherenceRow(result: Adherence, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(result.taken.toString(), stringResource(R.string.stat_taken))
        Stat(result.missed.toString(), stringResource(R.string.stat_missed))
        Stat(result.skipped.toString(), stringResource(R.string.stat_skipped))
        Stat(result.fraction?.let { "${(it * 100).toInt()}%" } ?: "—", stringResource(R.string.stat_adherence))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun HistoryDay(date: LocalDate, status: DayStatus, isToday: Boolean, onClick: () -> Unit) {
    val filled = status == DayStatus.DONE || status == DayStatus.PARTIAL || status == DayStatus.MISSED
    val color = status.color()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (filled) color else Color.Transparent)
            .border(
                width = if (isToday) 2.dp else 1.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary else if (filled) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = when (status) {
                DayStatus.DONE -> MaterialTheme.colorScheme.onPrimary
                DayStatus.PARTIAL -> MaterialTheme.colorScheme.onTertiary
                DayStatus.MISSED -> MaterialTheme.colorScheme.onError
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** A day's doses for one course, answerable — how missed days get filled in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(date: LocalDate, course: Course, viewModel: MedicineViewModel, onDismiss: () -> Unit) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val now = rememberNow()
    val doses = Schedule.dosesOn(date, snapshot.medicines.filter { it.courseId == course.id }, snapshot.courses, snapshot.logs)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(formatDate(date), style = MaterialTheme.typography.titleLarge)
            if (doses.isEmpty()) {
                Text(stringResource(R.string.day_nothing_scheduled), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            doses.forEach { dose ->
                DoseRow(dose = dose, now = now, onTake = viewModel::take, onSkip = viewModel::skip, onUndo = viewModel::undo, showTime = true)
            }
            val unanswered = doses.filter { it.log == null && it.key.dateTime <= now }
            if (unanswered.size > 1) {
                FilledTonalButton(
                    onClick = { unanswered.forEach { viewModel.take(it.key, it.medicine.doseAmount) } },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.action_mark_all_taken)) }
            }
        }
    }
}

/** "Also save to Documents": whether files added to a course are copied into Documents too. */
@Composable
fun SaveToDocumentsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.setting_save_to_documents), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.setting_save_to_documents_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.padding(start = 12.dp))
    }
}
