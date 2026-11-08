package com.wwwescape.medicinereminder.ui.screens.documents

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Biotech
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.DocumentCategory
import com.wwwescape.medicinereminder.data.db.HealthDocument
import com.wwwescape.medicinereminder.ui.MedicineViewModel
import com.wwwescape.medicinereminder.ui.components.EmptyStateNotice
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.StoredImage
import com.wwwescape.medicinereminder.ui.components.shareStoredFile
import com.wwwescape.medicinereminder.ui.screens.courses.PRESCRIPTION_MIME_TYPES
import com.wwwescape.medicinereminder.util.formatDate

/**
 * The document registry: health documents kept on their own, outside any course — glasses
 * prescriptions, lab reports, scans, insurance cards. Filter by kind; tap one for its details.
 */
@Composable
fun DocumentsScreen(
    viewModel: MedicineViewModel,
    onOpenDocument: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<DocumentCategory?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importDocuments(uris, filter ?: DocumentCategory.OTHER, onSingle = onOpenDocument)
    }
    val shown = documents.filter { filter == null || it.category == filter }

    Box(modifier = modifier.fillMaxSize()) {
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    // Only the kinds that have documents, so the row stays short.
                    val present = DocumentCategory.entries.filter { category -> documents.any { it.category == category } }
                    if (present.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.documents_all)) })
                            present.forEach { category ->
                                FilterChip(
                                    selected = filter == category,
                                    onClick = { filter = if (filter == category) null else category },
                                    label = { Text(stringResource(category.labelRes)) },
                                    leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                }
                if (documents.isEmpty()) {
                    item {
                        EmptyStateNotice(
                            title = stringResource(R.string.documents_empty_title),
                            body = stringResource(R.string.documents_empty_body),
                            icon = Icons.Rounded.Folder,
                        )
                    }
                }
                shown.groupBy { it.category }.toSortedMap().forEach { (category, inCategory) ->
                    if (filter == null) {
                        item(key = "label-$category") { SectionLabel(stringResource(category.labelRes)) }
                    }
                    items(inCategory, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            courseName = document.sourceCourseId?.let { snapshot.courses[it]?.name }?.takeIf { it != document.title },
                            onClick = { onOpenDocument(document.id) },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { picker.launch(PRESCRIPTION_MIME_TYPES) },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.action_add_document)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun DocumentRow(document: HealthDocument, courseName: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val file = MedicineRepository.documentFile(context, document.fileName)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            StoredImage(
                file,
                contentDescription = null,
                mimeType = document.mimeType,
                maxPx = 240,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(formatDate(document.date), courseName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { shareDocument(context, document) }) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
            }
        }
    }
}

/** Hands a document to another app — an optician, a doctor, a chat — read-only. */
fun shareDocument(context: Context, document: HealthDocument) =
    shareStoredFile(context, MedicineRepository.documentFile(context, document.fileName), document.mimeType, document.title)

val DocumentCategory.labelRes: Int
    get() = when (this) {
        DocumentCategory.PRESCRIPTION -> R.string.doc_prescription
        DocumentCategory.GLASSES -> R.string.doc_glasses
        DocumentCategory.LAB_REPORT -> R.string.doc_lab_report
        DocumentCategory.IMAGING -> R.string.doc_imaging
        DocumentCategory.DISCHARGE -> R.string.doc_discharge
        DocumentCategory.VACCINATION -> R.string.doc_vaccination
        DocumentCategory.INSURANCE -> R.string.doc_insurance
        DocumentCategory.BILL -> R.string.doc_bill
        DocumentCategory.OTHER -> R.string.doc_other
    }

val DocumentCategory.icon: ImageVector
    get() = when (this) {
        DocumentCategory.PRESCRIPTION -> Icons.Rounded.Description
        DocumentCategory.GLASSES -> Icons.Rounded.Visibility
        DocumentCategory.LAB_REPORT -> Icons.Rounded.Biotech
        DocumentCategory.IMAGING -> Icons.Rounded.Image
        DocumentCategory.DISCHARGE -> Icons.Rounded.LocalHospital
        DocumentCategory.VACCINATION -> Icons.Rounded.Vaccines
        DocumentCategory.INSURANCE -> Icons.Rounded.HealthAndSafety
        DocumentCategory.BILL -> Icons.Rounded.Receipt
        DocumentCategory.OTHER -> Icons.Rounded.Badge
    }
