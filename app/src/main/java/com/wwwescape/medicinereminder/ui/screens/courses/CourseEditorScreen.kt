package com.wwwescape.medicinereminder.ui.screens.courses

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.ui.components.FormSection
import com.wwwescape.medicinereminder.ui.components.FieldHint
import com.wwwescape.medicinereminder.ui.components.EditorScaffold
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.StoredFile
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import com.wwwescape.medicinereminder.ui.components.PrescriptionThumbnail
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.openPrescription
import com.wwwescape.medicinereminder.util.formatDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class CourseEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private var original: Course? = null
    private var loadedId: Long? = null

    var name by mutableStateOf("")
    var condition by mutableStateOf("")
    var doctorName by mutableStateOf("")
    var doctorSpecialty by mutableStateOf("")
    var doctorPhone by mutableStateOf("")
    var clinicName by mutableStateOf("")
    var clinicAddress by mutableStateOf("")
    var clinicPhone by mutableStateOf("")
    var patientId by mutableStateOf("")
    var notes by mutableStateOf("")
    var startDate by mutableStateOf(LocalDate.now())
    var ongoing by mutableStateOf(false)
    var lengthDays by mutableStateOf("30")
    var saving by mutableStateOf(false)
        private set

    /** Prescriptions picked before the course is saved: already copied into app storage, and
     * attached on save (or deleted if the editor is abandoned). */
    val pendingFiles = mutableStateListOf<StoredFile>()
    private var saved = false

    val endDate: LocalDate?
        get() = if (ongoing) null else lengthDays.toLongOrNull()?.takeIf { it > 0 }?.let { startDate.plusDays(it - 1) }

    /** Whether files attached on save are also copied into Documents. */
    val saveToDocuments: StateFlow<Boolean> = SettingsRepository.settingsFlow(application)
        .map { it.saveToDocuments }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSaveToDocuments(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setSaveToDocuments(app, enabled) }
    }

    val canSave get() = name.isNotBlank() && (ongoing || endDate != null) && !saving

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        if (id == 0L) return
        viewModelScope.launch {
            val course = MedicineRepository.getCourse(app, id) ?: return@launch
            original = course
            name = course.name
            condition = course.condition
            doctorName = course.doctorName
            doctorSpecialty = course.doctorSpecialty
            doctorPhone = course.doctorPhone
            clinicName = course.clinicName
            clinicAddress = course.clinicAddress
            clinicPhone = course.clinicPhone
            patientId = course.patientId
            notes = course.notes
            startDate = course.startDate
            ongoing = course.endDate == null
            course.endDate?.let { lengthDays = (ChronoUnit.DAYS.between(course.startDate, it) + 1).toString() }
        }
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri -> MedicineRepository.importPrescription(app, uri)?.let(pendingFiles::add) }
        }
    }

    fun removeFile(file: StoredFile) {
        pendingFiles.remove(file)
        viewModelScope.launch { MedicineRepository.deleteStoredFile(app, file.fileName) }
    }

    fun save(onSaved: (Long) -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val course = (original ?: Course(name = "", startDate = startDate, endDate = null)).copy(
                name = name.trim(),
                condition = condition.trim(),
                doctorName = doctorName.trim(),
                doctorSpecialty = doctorSpecialty.trim(),
                doctorPhone = doctorPhone.trim(),
                clinicName = clinicName.trim(),
                clinicAddress = clinicAddress.trim(),
                clinicPhone = clinicPhone.trim(),
                patientId = patientId.trim(),
                notes = notes.trim(),
                startDate = startDate,
                endDate = endDate,
            )
            val id = MedicineRepository.saveCourse(app, course)
            pendingFiles.forEach { MedicineRepository.attachPrescription(app, id, it) }
            saved = true
            onSaved(id)
        }
    }

    override fun onCleared() {
        if (!saved) {
            val files = pendingFiles.toList()
            // The ViewModel scope is already cancelled here; the deletes are quick file ops.
            files.forEach { MedicineRepository.prescriptionFile(app, it.fileName).delete() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorScreen(
    courseId: Long,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    editor: CourseEditorViewModel = viewModel(key = "course-editor-$courseId"),
) {
    LaunchedEffect(courseId) { editor.load(courseId) }
    var pickingDate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) editor.addFiles(uris)
    }

    EditorScaffold(
        actionLabel = stringResource(if (courseId == 0L) R.string.action_create_course else R.string.action_save),
        actionEnabled = editor.canSave,
        onAction = { editor.save(onSaved) },
        modifier = modifier,
    ) {
        FormSection(stringResource(R.string.section_course)) {
            OutlinedTextField(
                value = editor.name,
                onValueChange = { editor.name = it },
                label = { Text(stringResource(R.string.field_course_name)) },
                placeholder = { Text(stringResource(R.string.field_course_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            DetailField(editor.condition, { editor.condition = it }, R.string.field_condition, placeholder = R.string.field_condition_hint)
        }

        FormSection(stringResource(R.string.section_duration)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { pickingDate = true }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.field_start_date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDate(editor.startDate), style = MaterialTheme.typography.bodyLarge)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.action_pick_date), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !editor.ongoing,
                    onClick = { editor.ongoing = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.duration_fixed)) }
                SegmentedButton(
                    selected = editor.ongoing,
                    onClick = { editor.ongoing = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.duration_ongoing)) }
            }
            if (!editor.ongoing) {
                OutlinedTextField(
                    value = editor.lengthDays,
                    onValueChange = { value -> editor.lengthDays = value.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.field_length_days)) },
                    supportingText = {
                        editor.endDate?.let { Text(stringResource(R.string.field_length_days_ends, formatDate(it))) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        FormSection(stringResource(R.string.section_doctor_optional)) {
            DetailField(editor.doctorName, { editor.doctorName = it }, R.string.field_doctor_name, KeyboardCapitalization.Words)
            DetailField(editor.doctorSpecialty, { editor.doctorSpecialty = it }, R.string.field_doctor_specialty, KeyboardCapitalization.Words)
            DetailField(editor.doctorPhone, { editor.doctorPhone = it }, R.string.field_doctor_phone, keyboardType = KeyboardType.Phone)
            DetailField(editor.clinicName, { editor.clinicName = it }, R.string.field_clinic_name, KeyboardCapitalization.Words)
            DetailField(editor.clinicAddress, { editor.clinicAddress = it }, R.string.field_clinic_address, KeyboardCapitalization.Words, singleLine = false)
            DetailField(editor.clinicPhone, { editor.clinicPhone = it }, R.string.field_clinic_phone, keyboardType = KeyboardType.Phone)
            DetailField(editor.patientId, { editor.patientId = it }, R.string.field_patient_id, KeyboardCapitalization.Characters)
        }

        if (courseId == 0L) {
            FormSection(stringResource(R.string.section_prescriptions_optional)) {
                val saveToDocuments by editor.saveToDocuments.collectAsStateWithLifecycle()
                SaveToDocumentsSwitch(checked = saveToDocuments, onCheckedChange = editor::setSaveToDocuments)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    editor.pendingFiles.forEach { file ->
                        PrescriptionThumbnail(
                            file = MedicineRepository.prescriptionFile(context, file.fileName),
                            displayName = file.displayName,
                            mimeType = file.mimeType,
                            onClick = { openPrescription(context, file.fileName, file.mimeType, file.displayName) },
                            onLongClick = { editor.removeFile(file) },
                        )
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
                FieldHint(stringResource(R.string.prescriptions_editor_hint))
            }
        }

        FormSection(stringResource(R.string.section_notes)) {
            OutlinedTextField(
                value = editor.notes,
                onValueChange = { editor.notes = it },
                placeholder = { Text(stringResource(R.string.field_course_notes_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = editor.startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        editor.startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/** An optional one-line detail of the course, such as the doctor's phone number. */
@Composable
private fun DetailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = placeholder?.let { { Text(stringResource(it)) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
