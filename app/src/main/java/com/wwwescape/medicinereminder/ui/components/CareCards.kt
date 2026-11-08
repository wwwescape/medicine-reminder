package com.wwwescape.medicinereminder.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.db.Appointment
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.reminders.dateTime
import com.wwwescape.medicinereminder.util.formatAppointmentWhen
import java.time.LocalDateTime

fun Course.hasCareDetails(): Boolean =
    listOf(doctorName, doctorSpecialty, doctorPhone, clinicName, clinicAddress, clinicPhone, patientId).any { it.isNotBlank() }

/** Who looks after a course and where: the doctor, the clinic, how to reach them, and the
 * patient number to quote. Phone numbers dial, the address opens in a maps app. */
@Composable
fun CareDetailsCard(course: Course, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        val rows = buildList<@Composable () -> Unit> {
            if (course.doctorName.isNotBlank() || course.doctorSpecialty.isNotBlank()) add {
                DetailRow(
                    icon = Icons.Rounded.Person,
                    title = course.doctorName.ifBlank { course.doctorSpecialty },
                    subtitle = course.doctorSpecialty.takeIf { course.doctorName.isNotBlank() && it.isNotBlank() },
                    action = course.doctorPhone.takeIf { it.isNotBlank() }?.let { phone ->
                        Icons.Rounded.Call to { dial(context, phone) }
                    },
                    actionLabel = stringResource(R.string.action_call),
                )
            }
            if (course.doctorName.isBlank() && course.doctorSpecialty.isBlank() && course.doctorPhone.isNotBlank()) add {
                DetailRow(Icons.Rounded.Call, course.doctorPhone, null, Icons.Rounded.Call to { dial(context, course.doctorPhone) }, stringResource(R.string.action_call))
            }
            if (course.clinicName.isNotBlank() || course.clinicAddress.isNotBlank()) add {
                DetailRow(
                    icon = Icons.Rounded.LocalHospital,
                    title = course.clinicName.ifBlank { course.clinicAddress },
                    subtitle = course.clinicAddress.takeIf { course.clinicName.isNotBlank() && it.isNotBlank() },
                    action = Icons.Rounded.Directions to { openMap(context, listOf(course.clinicName, course.clinicAddress).filter { it.isNotBlank() }.joinToString(", ")) },
                    actionLabel = stringResource(R.string.action_directions),
                )
            }
            if (course.clinicPhone.isNotBlank()) add {
                DetailRow(
                    Icons.Rounded.Call,
                    course.clinicPhone,
                    stringResource(R.string.field_clinic_phone),
                    Icons.Rounded.Call to { dial(context, course.clinicPhone) },
                    stringResource(R.string.action_call),
                )
            }
            if (course.patientId.isNotBlank()) add {
                DetailRow(
                    Icons.Rounded.Badge,
                    course.patientId,
                    stringResource(R.string.field_patient_id),
                    Icons.Rounded.ContentCopy to { copy(context, course.patientId) },
                    stringResource(R.string.action_copy),
                )
            }
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            row()
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    action: Pair<ImageVector, () -> Unit>?,
    actionLabel: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) {
            IconButton(onClick = action.second) { Icon(action.first, contentDescription = actionLabel) }
        }
    }
}

/** A follow-up visit: what, when, with whom and where. Past visits are dimmed. */
@Composable
fun AppointmentRow(appointment: Appointment, now: LocalDateTime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val past = appointment.dateTime < now
    val tint = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = tint)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                appointment.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(formatAppointmentWhen(context, appointment), style = MaterialTheme.typography.bodyMedium, color = tint)
            val where = listOf(appointment.doctorName, appointment.place).filter { it.isNotBlank() }.joinToString(" · ")
            if (where.isNotEmpty()) {
                Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

fun dial(context: Context, phone: String) = launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))

fun openMap(context: Context, query: String) = launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(text, text))
    Toast.makeText(context, R.string.toast_copied, Toast.LENGTH_SHORT).show()
}

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.toast_no_app, Toast.LENGTH_SHORT).show()
    }
}
