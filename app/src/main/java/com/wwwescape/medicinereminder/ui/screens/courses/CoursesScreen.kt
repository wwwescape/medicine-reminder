package com.wwwescape.medicinereminder.ui.screens.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.Snapshot
import com.wwwescape.medicinereminder.data.db.Course
import com.wwwescape.medicinereminder.data.schedule.adherence
import com.wwwescape.medicinereminder.ui.MedicineViewModel
import com.wwwescape.medicinereminder.ui.components.EmptyStateNotice
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.rememberNow
import com.wwwescape.medicinereminder.ui.icons.MedicineIcon
import com.wwwescape.medicinereminder.util.courseProgressText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Composable
fun CoursesScreen(
    viewModel: MedicineViewModel,
    onOpenCourse: (Long) -> Unit,
    onNewCourse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val now = rememberNow()
    val today = now.toLocalDate()
    val courses = snapshot.courses.values
    val active = courses.filter { it.startDate <= today && (it.endDate == null || it.endDate >= today) }.sortedBy { it.startDate }
    val upcoming = courses.filter { it.startDate > today }.sortedBy { it.startDate }
    val finished = courses.filter { it.endDate != null && it.endDate < today }.sortedByDescending { it.endDate }

    Box(modifier = modifier.fillMaxSize()) {
        ReadableWidth {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (snapshot.loaded && courses.isEmpty()) {
                    item {
                        EmptyStateNotice(
                            title = stringResource(R.string.courses_empty_title),
                            body = stringResource(R.string.courses_empty_body),
                            icon = Icons.Rounded.MedicalServices,
                        )
                    }
                }
                listOf(
                    R.string.courses_active to active,
                    R.string.courses_upcoming to upcoming,
                    R.string.courses_finished to finished,
                ).forEach { (label, list) ->
                    if (list.isNotEmpty()) {
                        item(key = "label-$label") { SectionLabel(stringResource(label)) }
                        items(list, key = { it.id }) { course ->
                            CourseCard(course, snapshot, now, onClick = { onOpenCourse(course.id) })
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewCourse,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.action_new_course)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun CourseCard(course: Course, snapshot: Snapshot, now: LocalDateTime, onClick: () -> Unit) {
    val context = LocalContext.current
    val medicines = snapshot.medicines.filter { it.courseId == course.id }
    val result = remember(snapshot, course) {
        adherence(now, medicines, snapshot.courses, snapshot.logs, course.startDate)
    }
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleLarge)
            Text(
                listOf(courseProgressText(context, course, now.toLocalDate()), course.doctorName)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            courseFraction(course, now.toLocalDate())?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    medicines.take(5).forEach { MedicineIcon(it, width = 36.dp) }
                }
                result.fraction?.let {
                    Text(
                        stringResource(R.string.adherence_percent, (it * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** How far through a fixed-length course [today] is, or null for an ongoing one. */
fun courseFraction(course: Course, today: LocalDate): Float? {
    val end = course.endDate ?: return null
    val total = ChronoUnit.DAYS.between(course.startDate, end) + 1
    val done = (ChronoUnit.DAYS.between(course.startDate, today) + 1).coerceIn(0, total)
    return done.toFloat() / total
}
