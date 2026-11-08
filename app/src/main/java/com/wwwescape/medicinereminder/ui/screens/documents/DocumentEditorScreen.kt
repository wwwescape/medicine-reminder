package com.wwwescape.medicinereminder.ui.screens.documents

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.DocumentCategory
import com.wwwescape.medicinereminder.data.db.HealthDocument
import com.wwwescape.medicinereminder.ui.components.DateDialog
import androidx.compose.ui.Alignment
import com.wwwescape.medicinereminder.ui.components.FormSection
import com.wwwescape.medicinereminder.ui.components.FieldHint
import com.wwwescape.medicinereminder.ui.components.EditorScaffold
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.StoredImage
import com.wwwescape.medicinereminder.ui.components.openStoredFile
import com.wwwescape.medicinereminder.util.formatDate
import kotlinx.coroutines.launch
import java.time.LocalDate

class DocumentEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()
    private var loaded = false

    var document by mutableStateOf<HealthDocument?>(null)
        private set
    var courseName by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf("")
    var category by mutableStateOf(DocumentCategory.OTHER)
    var date by mutableStateOf(LocalDate.now())
    var notes by mutableStateOf("")

    val canSave get() = document != null && title.isNotBlank()

    fun load(id: Long) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val found = MedicineRepository.getDocument(app, id) ?: return@launch
            document = found
            title = found.title
            category = found.category
            date = found.date
            notes = found.notes
            courseName = found.sourceCourseId?.let { MedicineRepository.getCourse(app, it)?.name }
        }
    }

    fun current(): HealthDocument? = document?.copy(title = title.trim(), category = category, date = date, notes = notes.trim())

    fun save(onSaved: () -> Unit) {
        val updated = current() ?: return
        viewModelScope.launch {
            MedicineRepository.saveDocument(app, updated)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val existing = document ?: return
        viewModelScope.launch {
            MedicineRepository.deleteDocument(app, existing)
            onDeleted()
        }
    }
}

/** One document: a large preview, Open and Share, and its name, kind, date and notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentEditorScreen(
    documentId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    editor: DocumentEditorViewModel = viewModel(key = "document-$documentId"),
) {
    LaunchedEffect(documentId) { editor.load(documentId) }
    val context = LocalContext.current
    val document = editor.document ?: return
    val file = MedicineRepository.documentFile(context, document.fileName)
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    EditorScaffold(
        actionLabel = stringResource(R.string.action_save),
        actionEnabled = editor.canSave,
        onAction = { editor.save(onDone) },
        modifier = modifier,
    ) {
        StoredImage(
            file,
            contentDescription = document.title,
            mimeType = document.mimeType,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable { openStoredFile(context, file, document.mimeType, editor.title.ifBlank { document.title }) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { openStoredFile(context, file, document.mimeType, editor.title.ifBlank { document.title }) }) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_open), modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(onClick = { editor.current()?.let { shareDocument(context, it) } }) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_share), modifier = Modifier.padding(start = 8.dp))
            }
        }
        editor.courseName?.let { FieldHint(stringResource(R.string.document_from_course, it)) }

        FormSection(stringResource(R.string.section_document)) {
            OutlinedTextField(
                value = editor.title,
                onValueChange = { editor.title = it },
                label = { Text(stringResource(R.string.field_document_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { pickingDate = true }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(stringResource(R.string.field_document_date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDate(editor.date), style = MaterialTheme.typography.bodyLarge)
            }
        }

        FormSection(stringResource(R.string.section_document_kind)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentCategory.entries.forEach { category ->
                    FilterChip(
                        selected = editor.category == category,
                        onClick = { editor.category = category },
                        label = { Text(stringResource(category.labelRes)) },
                        leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        FormSection(stringResource(R.string.section_notes)) {
            OutlinedTextField(
                value = editor.notes,
                onValueChange = { editor.notes = it },
                placeholder = { Text(stringResource(R.string.field_document_notes_hint)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_delete_document), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (pickingDate) {
        DateDialog(editor.date, onDismiss = { pickingDate = false }) {
            editor.date = it
            pickingDate = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_body)) },
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
