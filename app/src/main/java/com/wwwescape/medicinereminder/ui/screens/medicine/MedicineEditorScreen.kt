package com.wwwescape.medicinereminder.ui.screens.medicine

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.db.DoseUnit
import com.wwwescape.medicinereminder.data.db.Frequency
import com.wwwescape.medicinereminder.data.db.MedicineShape
import com.wwwescape.medicinereminder.ui.components.DateDialog
import com.wwwescape.medicinereminder.ui.components.EditorScaffold
import com.wwwescape.medicinereminder.ui.components.FieldHint
import com.wwwescape.medicinereminder.ui.components.FormSection
import com.wwwescape.medicinereminder.ui.components.TimeDialog
import com.wwwescape.medicinereminder.ui.components.rememberWeekStart
import com.wwwescape.medicinereminder.ui.components.weekDays
import com.wwwescape.medicinereminder.ui.icons.MedicineColors
import com.wwwescape.medicinereminder.ui.icons.MedicineIcon
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatDate
import com.wwwescape.medicinereminder.util.formatMinute
import com.wwwescape.medicinereminder.util.labelRes
import com.wwwescape.medicinereminder.util.scheduleText
import com.wwwescape.medicinereminder.util.unitText
import java.time.LocalDate
import java.time.format.TextStyle

private enum class DateField { START, END }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineEditorScreen(
    courseId: Long,
    medicineId: Long,
    onDone: () -> Unit,
    onAddNextStep: (Long) -> Unit,
    modifier: Modifier = Modifier,
    copyOf: Long = 0,
    editor: MedicineEditorViewModel = viewModel(key = "medicine-editor-$courseId-$medicineId-$copyOf"),
) {
    LaunchedEffect(courseId, medicineId, copyOf) { editor.load(courseId, medicineId, copyOf) }
    val context = LocalContext.current
    var editingTime by remember { mutableStateOf<Int?>(null) }
    var pickingDate by remember { mutableStateOf<DateField?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        actionLabel = stringResource(if (editor.isNew) R.string.action_add_medicine else R.string.action_save),
        actionEnabled = editor.canSave,
        onAction = { editor.save(onDone) },
        modifier = modifier,
    ) {
        PreviewHeader(editor)

        FormSection(stringResource(R.string.section_medicine)) {
            OutlinedTextField(
                value = editor.name,
                onValueChange = { editor.name = it },
                label = { Text(stringResource(R.string.field_medicine_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = editor.strength,
                onValueChange = { editor.strength = it },
                label = { Text(stringResource(R.string.field_strength)) },
                placeholder = { Text(stringResource(R.string.field_strength_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormSection(stringResource(R.string.section_dose)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { editor.changeAmount(-1) }) { Icon(Icons.Rounded.Remove, stringResource(R.string.action_less)) }
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = { value -> editor.amountText = value.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
                    singleLine = true,
                    isError = !editor.amountValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.width(88.dp),
                )
                FilledTonalIconButton(onClick = { editor.changeAmount(1) }) { Icon(Icons.Rounded.Add, stringResource(R.string.action_more)) }
                UnitDropdown(editor.doseUnit, editor.doseAmount, editor::selectUnit, Modifier.weight(1f))
            }
        }

        FormSection(stringResource(R.string.section_when)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Frequency.entries.forEach { frequency ->
                    FilterChip(
                        selected = editor.frequency == frequency,
                        onClick = { editor.frequency = frequency },
                        label = { Text(stringResource(frequency.labelRes)) },
                    )
                }
            }
            when (editor.frequency) {
                Frequency.EVERY_X_DAYS -> OutlinedTextField(
                    value = editor.intervalDays,
                    onValueChange = { value -> editor.intervalDays = value.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.field_interval_days)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Frequency.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekDays(rememberWeekStart()).forEach { day ->
                        val bit = day.value - 1
                        FilterChip(
                            selected = editor.weekdays and (1 shl bit) != 0,
                            onClick = { editor.toggleWeekday(bit) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0])) },
                        )
                    }
                }
                Frequency.AS_NEEDED -> FieldHint(stringResource(R.string.as_needed_hint))
                Frequency.DAILY -> Unit
            }

            if (editor.frequency != Frequency.AS_NEEDED) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TimesPerDayCounter(count = editor.times.size, onChange = editor::setTimesPerDay)
                editor.times.forEachIndexed { index, minute ->
                    TimeRow(
                        label = stringResource(R.string.dose_number, index + 1),
                        time = formatMinute(context, minute),
                        onClick = { editingTime = index },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(stringResource(R.string.field_reminders), style = MaterialTheme.typography.bodyLarge)
                        FieldHint(stringResource(R.string.field_reminders_hint))
                    }
                    Switch(checked = editor.remindersEnabled, onCheckedChange = { editor.remindersEnabled = it })
                }
            }
        }

        FormSection(stringResource(R.string.section_instructions)) {
            InstructionPicker(editor)
            OutlinedTextField(
                value = editor.instructions,
                onValueChange = { editor.instructions = it },
                label = { Text(stringResource(R.string.field_instructions)) },
                placeholder = { Text(stringResource(R.string.field_instructions_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormSection(stringResource(R.string.section_looks)) {
            Text(stringResource(R.string.section_shape) + " · " + stringResource(editor.shape.labelRes), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MedicineShape.entries.forEach { shape ->
                    val selected = shape == editor.shape
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 64.dp, height = 50.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = MaterialTheme.shapes.medium,
                            )
                            .clickable { editor.selectShape(shape) },
                    ) {
                        MedicineIcon(shape, editor.color1, editor.color2, width = 52.dp)
                    }
                }
            }
            Text(stringResource(if (editor.shape.twoTone) R.string.section_color_first else R.string.section_color), style = MaterialTheme.typography.labelLarge)
            ColorSwatches(selected = editor.color1, onSelect = { editor.color1 = it })
            if (editor.shape.twoTone) {
                Text(stringResource(R.string.section_color_second), style = MaterialTheme.typography.labelLarge)
                ColorSwatches(selected = editor.color2, onSelect = { editor.color2 = it })
            }
            Text(stringResource(R.string.section_photos), style = MaterialTheme.typography.labelLarge)
            FieldHint(stringResource(R.string.photos_hint))
            MedicinePhotos(editor)
        }

        FormSection(stringResource(R.string.section_medicine_dates)) {
            if (editor.isNextStep) {
                FieldHint(stringResource(if (editor.startDate == null) R.string.next_step_hint_no_end else R.string.next_step_hint))
            }
            val course = editor.course
            DateRow(
                label = stringResource(R.string.field_starts),
                value = editor.startDate?.let(::formatDate) ?: stringResource(R.string.date_with_course_start, course?.startDate?.let(::formatDate) ?: ""),
                onPick = { pickingDate = DateField.START },
                onReset = if (editor.startDate != null) ({ editor.startDate = null }) else null,
            )
            DateRow(
                label = stringResource(R.string.field_ends),
                value = editor.endDate?.let(::formatDate)
                    ?: course?.endDate?.let { stringResource(R.string.date_with_course_end, formatDate(it)) }
                    ?: stringResource(R.string.date_ongoing),
                onPick = { pickingDate = DateField.END },
                onReset = if (editor.endDate != null) ({ editor.endDate = null }) else null,
            )
        }

        FormSection(stringResource(R.string.section_notes)) {
            OutlinedTextField(
                value = editor.notes,
                onValueChange = { editor.notes = it },
                placeholder = { Text(stringResource(R.string.field_medicine_notes_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!editor.isNew) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAddNextStep(medicineId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_add_next_step), modifier = Modifier.padding(start = 8.dp))
                }
                FieldHint(stringResource(R.string.add_next_step_hint))
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_delete_medicine), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    editingTime?.let { index ->
        TimeDialog(
            initialMinute = editor.times.getOrElse(index) { 8 * 60 },
            onDismiss = { editingTime = null },
            onConfirm = { minute ->
                if (editor.setTime(index, minute)) {
                    editingTime = null
                } else {
                    Toast.makeText(context, R.string.time_taken, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    pickingDate?.let { field ->
        val initial = (if (field == DateField.START) editor.startDate ?: editor.course?.startDate else editor.endDate ?: editor.course?.endDate)
            ?: LocalDate.now()
        DateDialog(initial, onDismiss = { pickingDate = null }) { date ->
            if (field == DateField.START) editor.startDate = date else editor.endDate = date
            pickingDate = null
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_medicine_title)) },
            text = { Text(stringResource(R.string.delete_medicine_body)) },
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

/** The medicine as it will look in a list: its icon, name, dose and when it's taken. */
@Composable
private fun PreviewHeader(editor: MedicineEditorViewModel) {
    val context = LocalContext.current
    val draft = editor.draft()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        MedicineIcon(editor.shape, editor.color1, editor.color2, width = 96.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                editor.name.ifBlank { stringResource(R.string.medicine_preview_name) },
                style = MaterialTheme.typography.titleLarge,
                color = if (editor.name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(doseText(context, draft), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(scheduleText(context, draft), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "Times a day" with − and + either side of the count. */
@Composable
private fun TimesPerDayCounter(count: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.field_times_a_day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(count - 1) }, enabled = count > 1) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.action_less))
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        FilledTonalIconButton(onClick = { onChange(count + 1) }, enabled = count < MAX_TIMES_PER_DAY) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_more))
        }
    }
}

/** One dose time; tap to change it. */
@Composable
private fun TimeRow(label: String, time: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Quick instructions for the kind of medicine. Where a medicine can go in several places (drops:
 * eyes, ears, nose, mouth), the place is picked first and only its options are shown. Other rows
 * (how to take it, food) are shown as they are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstructionPicker(editor: MedicineEditorViewModel) {
    val set = instructionSuggestions(editor.shape)
    fun isSet(text: String) = editor.instructions.split(",").any { it.trim().equals(text, ignoreCase = true) }

    if (set.areas.isNotEmpty()) {
        val areaTexts = set.areas.associateWith { area -> area.options.map { stringResource(it) } }
        val chosen = when (val choice = editor.instructionArea) {
            null -> set.areas.firstOrNull { area -> areaTexts.getValue(area).any(::isSet) }
            0 -> null
            else -> set.areas.firstOrNull { it.label == choice }
        }
        LabelledChips(stringResource(R.string.instr_group_where)) {
            set.areas.forEach { area ->
                FilterChip(
                    selected = area == chosen,
                    onClick = {
                        if (area == chosen) {
                            editor.removeInstructions(areaTexts.getValue(area))
                            editor.instructionArea = 0
                        } else {
                            editor.removeInstructions(areaTexts.filterKeys { it != area }.values.flatten())
                            editor.instructionArea = area.label
                        }
                    },
                    label = { Text(stringResource(area.label!!)) },
                )
            }
        }
        if (chosen != null) {
            val options = areaTexts.getValue(chosen)
            LabelledChips(label = null) {
                options.forEach { text ->
                    FilterChip(
                        selected = isSet(text),
                        onClick = { editor.toggleInstruction(text, replaces = if (chosen.oneOf) options else emptyList()) },
                        label = { Text(text) },
                    )
                }
            }
        }
    }
    set.groups.forEach { group ->
        val options = group.options.map { stringResource(it) }
        LabelledChips(group.label?.let { stringResource(it) }) {
            options.forEach { text ->
                FilterChip(
                    selected = isSet(text),
                    onClick = { editor.toggleInstruction(text, replaces = if (group.oneOf) options else emptyList()) },
                    label = { Text(text) },
                )
            }
        }
    }
}

/** A row of chips with a short label in a fixed column on the left, so rows line up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelledChips(label: String?, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) { chips() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MedicineColors.forEach { color ->
            val isSelected = color == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(color) },
            ) {
                if (isSelected) {
                    val light = Color(color).let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue } > 0.6f
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = if (light) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(selected: DoseUnit, amount: Double, onSelect: (DoseUnit) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    fun label(unit: DoseUnit) = unitText(context, unit, amount).substringAfter(' ')
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = label(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DoseUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(label(unit)) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DateRow(label: String, value: String, onPick: () -> Unit, onReset: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onPick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (onReset != null) TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
    }
}

private val Frequency.labelRes: Int
    get() = when (this) {
        Frequency.DAILY -> R.string.frequency_daily
        Frequency.EVERY_X_DAYS -> R.string.frequency_every_x_days
        Frequency.WEEKDAYS -> R.string.frequency_weekdays
        Frequency.AS_NEEDED -> R.string.frequency_as_needed
    }

/** A set of quick instructions, labelled ("Eyes", "Food"). In a [oneOf] set the options replace
 * each other, like left eye / right eye / both eyes. */
private class InstructionGroup(val label: Int?, val options: List<Int>, val oneOf: Boolean = false)

/** [areas] are places the medicine can go, picked one at a time; [groups] are always shown. */
private class InstructionSet(val areas: List<InstructionGroup> = emptyList(), val groups: List<InstructionGroup> = emptyList())

private val EYES = InstructionGroup(R.string.instr_group_eyes, listOf(R.string.instr_both_eyes, R.string.instr_left_eye, R.string.instr_right_eye), oneOf = true)
private val EARS = InstructionGroup(R.string.instr_group_ears, listOf(R.string.instr_both_ears, R.string.instr_left_ear, R.string.instr_right_ear), oneOf = true)
private val NOSE = InstructionGroup(R.string.instr_group_nose, listOf(R.string.instr_each_nostril, R.string.instr_left_nostril, R.string.instr_right_nostril), oneOf = true)
private val MOUTH = InstructionGroup(R.string.instr_group_mouth, listOf(R.string.instr_by_mouth, R.string.instr_under_tongue, R.string.instr_in_water))
private val SKIN = InstructionGroup(R.string.instr_group_skin, listOf(R.string.instr_thin_layer, R.string.instr_affected_area))
private val FOOD = InstructionGroup(
    R.string.instr_group_food,
    listOf(R.string.instr_after_food, R.string.instr_before_food, R.string.instr_with_food, R.string.instr_empty_stomach),
    oneOf = true,
)
private val SWALLOW_TABLET = InstructionGroup(
    R.string.instr_group_how,
    listOf(R.string.instr_swallow_whole, R.string.instr_chew, R.string.instr_dissolve, R.string.instr_under_tongue),
    oneOf = true,
)
private val SWALLOW_CAPSULE = InstructionGroup(
    R.string.instr_group_how,
    listOf(R.string.instr_swallow_whole, R.string.instr_sprinkle),
    oneOf = true,
)
/** Injection sites aren't one-of: rotating between them is the point. */
private val INJECTION_SITE = InstructionGroup(
    R.string.instr_group_where,
    listOf(R.string.instr_site_abdomen, R.string.instr_site_thigh, R.string.instr_site_arm, R.string.instr_site_buttock),
)
private val PATCH_SITE = InstructionGroup(
    R.string.instr_group_where,
    listOf(R.string.instr_site_arm, R.string.instr_site_chest, R.string.instr_site_back, R.string.instr_site_hip),
)
private fun extras(vararg options: Int) = InstructionGroup(null, options.toList())

/** Quick instructions that fit the kind of medicine. */
private fun instructionSuggestions(shape: MedicineShape): InstructionSet = when (shape) {
    MedicineShape.CAPSULE, MedicineShape.SOFTGEL -> InstructionSet(groups = listOf(SWALLOW_CAPSULE, FOOD, extras(R.string.instr_with_water)))
    MedicineShape.DROPS -> InstructionSet(areas = listOf(EYES, EARS, NOSE, MOUTH), groups = listOf(extras(R.string.instr_shake_well)))
    MedicineShape.SYRUP -> InstructionSet(groups = listOf(FOOD, extras(R.string.instr_shake_well, R.string.instr_measure)))
    MedicineShape.SACHET -> InstructionSet(groups = listOf(FOOD, extras(R.string.instr_dissolve)))
    MedicineShape.NASAL_SPRAY -> InstructionSet(groups = listOf(NOSE, extras(R.string.instr_shake_well)))
    MedicineShape.INHALER -> InstructionSet(groups = listOf(extras(R.string.instr_shake_well, R.string.instr_with_spacer, R.string.instr_rinse_mouth)))
    MedicineShape.NEBULIZER -> InstructionSet(
        groups = listOf(
            InstructionGroup(null, listOf(R.string.instr_use_mask, R.string.instr_mouthpiece), oneOf = true),
            extras(R.string.instr_rinse_mouth, R.string.instr_clean_cup),
        ),
    )
    // Insulin is the common case: timed with meals, kept cold, and the site rotated.
    MedicineShape.SYRINGE, MedicineShape.PEN, MedicineShape.VIAL, MedicineShape.AMPOULE ->
        InstructionSet(groups = listOf(INJECTION_SITE, FOOD, extras(R.string.instr_rotate_site, R.string.instr_keep_cold)))
    MedicineShape.TUBE -> InstructionSet(areas = listOf(SKIN, EYES), groups = listOf(extras(R.string.instr_wash_hands)))
    MedicineShape.PATCH -> InstructionSet(groups = listOf(PATCH_SITE, extras(R.string.instr_remove_old_patch, R.string.instr_new_spot)))
    MedicineShape.SUPPOSITORY -> InstructionSet(
        groups = listOf(
            InstructionGroup(R.string.instr_group_how, listOf(R.string.instr_rectal, R.string.instr_vaginal), oneOf = true),
            extras(R.string.instr_at_bedtime),
        ),
    )
    // Every tablet shape.
    else -> InstructionSet(groups = listOf(SWALLOW_TABLET, FOOD, extras(R.string.instr_with_water)))
}
