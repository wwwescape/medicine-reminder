package com.wwwescape.medicinereminder.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.backup.BackupData
import com.wwwescape.medicinereminder.data.backup.BackupException
import com.wwwescape.medicinereminder.ui.components.NavigationRow
import com.wwwescape.medicinereminder.ui.components.RowDivider
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.SettingsGroupCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Settings → Backup: export everything to a ZIP, or replace everything from one. */
@Composable
fun BackupSection(viewModel: SettingsViewModel) {
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    SectionLabel(stringResource(R.string.section_backup))
    SettingsGroupCard {
        NavigationRow(
            icon = Icons.Rounded.CloudUpload,
            title = stringResource(R.string.setting_export),
            subtitle = stringResource(R.string.setting_export_subtitle),
            onClick = { exporter.launch("medicine-reminder-backup-${LocalDate.now()}.zip") },
        )
        RowDivider()
        NavigationRow(
            icon = Icons.Rounded.CloudDownload,
            title = stringResource(R.string.setting_import),
            subtitle = stringResource(R.string.setting_import_subtitle),
            // Some file managers label ZIPs as generic binary, so offer those too.
            onClick = { importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        )
    }

    when (val state = viewModel.backupState) {
        BackupState.Idle -> Unit
        BackupState.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.backup_working), modifier = Modifier.padding(start = 16.dp))
                }
            },
        )
        is BackupState.Confirm -> AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            icon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.preview.exportedAt?.let {
                        Text(
                            stringResource(
                                R.string.backup_made_on,
                                it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
                            ),
                        )
                    }
                    Text(summary(state.preview.data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.backup_confirm_body), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.action_replace)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissBackup) { Text(stringResource(R.string.action_cancel)) } },
        )
        is BackupState.Exported -> ResultDialog(
            title = stringResource(R.string.backup_exported_title),
            body = stringResource(R.string.backup_exported_body, summary(state.data)),
            onDismiss = viewModel::dismissBackup,
        )
        is BackupState.Restored -> ResultDialog(
            title = stringResource(R.string.backup_restored_title),
            body = summary(state.data),
            onDismiss = viewModel::dismissBackup,
        )
        is BackupState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissBackup,
            icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_failed_title)) },
            text = {
                Text(
                    stringResource(
                        when (state.reason) {
                            BackupException.Reason.NOT_A_BACKUP -> R.string.backup_failed_not_backup
                            BackupException.Reason.NEWER_VERSION -> R.string.backup_failed_newer
                            BackupException.Reason.INCOMPLETE -> R.string.backup_failed_incomplete
                            BackupException.Reason.UNREADABLE -> R.string.backup_failed_unreadable
                        },
                    ),
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissBackup) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun ResultDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

/** "3 courses · 5 medicines · 120 dose records · 2 visits · 7 documents · 9 files". */
@Composable
private fun summary(data: BackupData): String {
    val files = data.prescriptionFiles.size + data.documents.size + data.medicines.sumOf { listOfNotNull(it.packagePhoto, it.dosePhoto).size }
    return stringResource(
        R.string.backup_summary,
        data.courses.size,
        data.medicines.size,
        data.doseLogs.size,
        data.appointments.size,
        data.documents.size,
        files,
    )
}
