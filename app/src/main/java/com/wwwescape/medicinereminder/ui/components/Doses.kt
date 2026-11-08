package com.wwwescape.medicinereminder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.schedule.DoseKey
import com.wwwescape.medicinereminder.data.schedule.ScheduledDose
import com.wwwescape.medicinereminder.ui.icons.MedicineIcon
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatMinute
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A dose is "due" for this long after its time; after that, unanswered means missed. */
private const val DUE_WINDOW_MINUTES = 60L

enum class DoseState { UPCOMING, DUE, MISSED, TAKEN, SKIPPED }

fun ScheduledDose.state(now: LocalDateTime): DoseState = when (log?.status) {
    DoseStatus.TAKEN -> DoseState.TAKEN
    DoseStatus.SKIPPED -> DoseState.SKIPPED
    null -> when {
        key.dateTime > now -> DoseState.UPCOMING
        key.dateTime.plusMinutes(DUE_WINDOW_MINUTES) > now -> DoseState.DUE
        else -> DoseState.MISSED
    }
}

/** One scheduled dose with its state and the buttons to answer it (or undo an answer). Past
 * doses can be answered too, so missed days can be filled in afterwards. */
@Composable
fun DoseRow(
    dose: ScheduledDose,
    now: LocalDateTime,
    onTake: (DoseKey, Double) -> Unit,
    onSkip: (DoseKey, Double) -> Unit,
    onUndo: (DoseKey) -> Unit,
    /** Changes when a taken dose was taken (epoch millis). */
    onEditTakenAt: (DoseKey, Long) -> Unit,
    modifier: Modifier = Modifier,
    showCourse: Boolean = false,
    /** For lists without time headings, such as one day's doses in a sheet. */
    showTime: Boolean = false,
) {
    val context = LocalContext.current
    val medicine = dose.medicine
    val state = dose.state(now)
    var editingTime by remember { mutableStateOf(false) }
    val (statusText, statusColor) = when (state) {
        DoseState.TAKEN -> stringResource(R.string.dose_taken_at, formatLoggedAt(dose.log!!.loggedAt)) to MaterialTheme.colorScheme.primary
        DoseState.SKIPPED -> stringResource(R.string.dose_skipped) to MaterialTheme.colorScheme.onSurfaceVariant
        DoseState.DUE -> stringResource(R.string.dose_due) to MaterialTheme.colorScheme.tertiary
        DoseState.MISSED -> stringResource(R.string.dose_missed) to MaterialTheme.colorScheme.error
        DoseState.UPCOMING -> null to Color.Unspecified
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedicineIcon(medicine, width = 52.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                if (showTime) {
                    Text(formatMinute(context, dose.minute), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(medicine.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(
                    doseText(context, medicine),
                    medicine.instructions.takeIf { it.isNotBlank() },
                    dose.course.name.takeIf { showCourse },
                ).joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state == DoseState.TAKEN && statusText != null) {
                    // Tap to correct the time: marked taken late, or taken earlier than noted.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = stringResource(R.string.action_change_taken_time)) { editingTime = true }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(statusText, style = MaterialTheme.typography.labelLarge, color = statusColor)
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.padding(start = 4.dp).size(16.dp),
                        )
                    }
                } else if (statusText != null) {
                    Text(statusText, style = MaterialTheme.typography.labelLarge, color = statusColor, modifier = Modifier.padding(top = 2.dp))
                }
            }
            when (state) {
                DoseState.TAKEN, DoseState.SKIPPED -> IconButton(onClick = { onUndo(dose.key) }) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.action_undo))
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedIconButton(onClick = { onSkip(dose.key, medicine.doseAmount) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_skip))
                    }
                    FilledIconButton(onClick = { onTake(dose.key, medicine.doseAmount) }) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_take))
                    }
                }
            }
        }
    }
    val log = dose.log
    if (editingTime && log != null) {
        val takenAt = Instant.ofEpochMilli(log.loggedAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
        TimeDialog(
            initialMinute = takenAt.hour * 60 + takenAt.minute,
            onDismiss = { editingTime = false },
            onConfirm = { minute ->
                editingTime = false
                onEditTakenAt(dose.key, takenAtMillis(dose, minute))
            },
        )
    }
}

/** The moment a dose was taken, from the time picked for it. That's on the dose's own day, unless
 * it's over 12 hours before the dose was due: then it was taken just after midnight (an 11 PM
 * dose taken at 12:30 AM). Never later than now. */
private fun takenAtMillis(dose: ScheduledDose, minute: Int): Long {
    var takenAt = dose.key.date.atTime(minute / 60, minute % 60)
    if (takenAt.isBefore(dose.key.dateTime.minusHours(12))) takenAt = takenAt.plusDays(1)
    val millis = takenAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return minOf(millis, System.currentTimeMillis())
}

private fun formatLoggedAt(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate() == java.time.LocalDate.now()) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    } else {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    }
    return time.format(pattern)
}
