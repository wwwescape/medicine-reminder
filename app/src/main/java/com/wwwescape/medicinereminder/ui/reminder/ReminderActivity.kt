package com.wwwescape.medicinereminder.ui.reminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.MedicineRepository
import com.wwwescape.medicinereminder.data.db.DoseStatus
import com.wwwescape.medicinereminder.data.reminders.ReminderNotifier
import com.wwwescape.medicinereminder.data.reminders.ReminderReceiver
import com.wwwescape.medicinereminder.data.reminders.Reminders
import com.wwwescape.medicinereminder.data.reminders.TestReminder
import com.wwwescape.medicinereminder.data.schedule.ScheduledDose
import com.wwwescape.medicinereminder.data.settings.AppSettings
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import com.wwwescape.medicinereminder.data.settings.ThemeMode
import com.wwwescape.medicinereminder.data.wear.WearProtocol
import com.wwwescape.medicinereminder.ui.components.StoredImage
import com.wwwescape.medicinereminder.ui.components.openStoredFile
import com.wwwescape.medicinereminder.ui.icons.MedicineIcon
import com.wwwescape.medicinereminder.ui.theme.MedicineReminderTheme
import com.wwwescape.medicinereminder.util.doseText
import com.wwwescape.medicinereminder.util.formatMinute
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The full-screen reminder: shown over the lock screen when a dose is due, with each medicine,
 * its dose and instructions, and Take / Skip per dose plus Take all and Snooze. It closes itself
 * once every dose in the slot is answered.
 */
class ReminderActivity : ComponentActivity() {

    private var date by mutableStateOf(LocalDate.now())
    private var minute by mutableStateOf(0)
    /** Opened by Settings → Test the reminder: a sample dose, and nothing is logged. */
    private var isTest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        enableEdgeToEdge()
        readSlot(intent)
        // Answered, snoozed or closed somewhere else (the notification, or the watch).
        lifecycleScope.launch {
            ReminderNotifier.closedSlots.collect { slot -> if (slot == currentSlot()) finish() }
        }

        setContent {
            val settings by remember { SettingsRepository.settingsFlow(applicationContext) }.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MedicineReminderTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.useDynamicColor,
                colorTheme = settings.colorTheme,
                themeContrast = settings.themeContrast,
                pureDark = settings.pureDark,
                absoluteDark = settings.absoluteDark,
            ) {
                ReminderContent(
                    date = date,
                    minute = minute,
                    snoozeMinutes = settings.reminders.snooze.minutes,
                    sample = if (isTest) remember { listOf(TestReminder.sampleDose(applicationContext)) } else null,
                    onClose = if (isTest) ::stopTest else ::closeWithoutAnswer,
                    onSnooze = if (isTest) ::stopTest else ::snooze,
                    onFinished = if (isTest) ::stopTest else ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSlot(intent)
    }

    private fun readSlot(intent: Intent) {
        isTest = intent.getBooleanExtra(EXTRA_TEST, false)
        date = LocalDate.ofEpochDay(intent.getLongExtra(Reminders.EXTRA_DATE, LocalDate.now().toEpochDay()))
        minute = intent.getIntExtra(Reminders.EXTRA_MINUTE, 0)
    }

    private fun currentSlot(): String =
        if (isTest) WearProtocol.TEST_SLOT else WearProtocol.slotId(date.toEpochDay(), minute)

    /** Every button ends a test the same way: stop the sound and close. */
    private fun stopTest() {
        ReminderNotifier.cancelTest(this)
        finish()
    }

    /** Stops the ringing but leaves the doses unanswered; a follow-up may still ring later. */
    private fun closeWithoutAnswer() {
        ReminderNotifier.cancel(this, date, minute)
        finish()
    }

    private fun snooze() {
        val app = applicationContext
        val (d, m) = date to minute
        lifecycleScope.launch {
            ReminderReceiver.snooze(app, d, m)
            finish()
        }
    }

    companion object {
        private const val EXTRA_TEST = "test"

        fun testIntent(context: Context): Intent =
            Intent(context, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_TEST, true)

        fun intent(context: Context, date: LocalDate, minute: Int): Intent =
            Intent(context, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(Reminders.EXTRA_DATE, date.toEpochDay())
                .putExtra(Reminders.EXTRA_MINUTE, minute)
    }
}

@Composable
private fun ReminderContent(
    date: LocalDate,
    minute: Int,
    snoozeMinutes: Int,
    sample: List<ScheduledDose>?,
    onClose: () -> Unit,
    onSnooze: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doses by remember(date, minute) { mutableStateOf<List<ScheduledDose>?>(null) }
    suspend fun refresh() {
        if (sample != null) {
            doses = sample
            return
        }
        val left = Reminders.unanswered(context.applicationContext, date, minute)
        doses = left
        if (left.isEmpty()) onFinished()
    }
    LaunchedEffect(date, minute) { refresh() }
    fun answer(dose: ScheduledDose, status: DoseStatus) {
        if (sample != null) return onClose()
        scope.launch {
            MedicineRepository.logDose(context.applicationContext, dose.key, status, dose.medicine.doseAmount)
            refresh()
        }
    }

    val single = doses.orEmpty().size == 1
    val compact = LocalConfiguration.current.screenHeightDp < 640

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Text(
                formatMinute(context, minute),
                style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = if (compact) 0.dp else 24.dp),
            )
            Text(
                stringResource(R.string.reminder_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .padding(vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                doses.orEmpty().forEach { dose ->
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            MedicineIcon(dose.medicine, width = if (single && !compact) 180.dp else 96.dp)
                            Text(dose.medicine.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                            Text(doseText(context, dose.medicine), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (dose.medicine.instructions.isNotBlank()) {
                                Text(dose.medicine.instructions, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            }
                            Text(dose.course.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // What the dose and its pack look like, to check against before taking it.
                            val photos = listOfNotNull(
                                dose.medicine.dosePhoto?.let { it to R.string.photo_dose },
                                dose.medicine.packagePhoto?.let { it to R.string.photo_package },
                            )
                            if (photos.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                                    photos.forEach { (fileName, label) ->
                                        val file = MedicineRepository.photoFile(context, fileName)
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            StoredImage(
                                                file,
                                                contentDescription = stringResource(label),
                                                modifier = Modifier
                                                    .size(if (compact) 96.dp else 132.dp)
                                                    .clip(MaterialTheme.shapes.large)
                                                    .clickable { openStoredFile(context, file, "image/jpeg", dose.medicine.name, overLockScreen = true) },
                                            )
                                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            // A single dose answers from the buttons below, which never scroll away.
                            if (!single) {
                                AnswerButtons(
                                    onSkip = { answer(dose, DoseStatus.SKIPPED) },
                                    onTake = { answer(dose, DoseStatus.TAKEN) },
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
            // With several doses, Take and Skip answer every dose still open.
            val many = doses.orEmpty().size > 1
            fun answerAll(status: DoseStatus) {
                if (sample != null) return onClose()
                scope.launch {
                    doses.orEmpty().forEach {
                        MedicineRepository.logDose(context.applicationContext, it.key, status, it.medicine.doseAmount)
                    }
                    refresh()
                }
            }
            val buttonHeight = if (compact) 56.dp else 64.dp
            val buttonModifier = Modifier.height(buttonHeight)
            val buttonPadding = PaddingValues(horizontal = 12.dp)
            // Take · Snooze on top, Skip · Close below: the likely answers first.
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { answerAll(DoseStatus.TAKEN) },
                        shape = MaterialTheme.shapes.large,
                        contentPadding = buttonPadding,
                        modifier = buttonModifier.weight(1f),
                    ) { ButtonLabel(Icons.Rounded.Check, stringResource(if (many) R.string.action_take_all else R.string.action_take)) }
                    FilledTonalButton(
                        onClick = onSnooze,
                        shape = MaterialTheme.shapes.large,
                        contentPadding = buttonPadding,
                        modifier = buttonModifier.weight(1f),
                    ) {
                        // Two lines, so the length is never cut short ("Snooze 10…").
                        ButtonLabel(
                            Icons.Rounded.Snooze,
                            stringResource(R.string.action_snooze),
                            stringResource(R.string.snooze_length_short, snoozeMinutes),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = { answerAll(DoseStatus.SKIPPED) },
                        shape = MaterialTheme.shapes.large,
                        contentPadding = buttonPadding,
                        modifier = buttonModifier.weight(1f),
                    ) { ButtonLabel(Icons.Rounded.SkipNext, stringResource(if (many) R.string.action_skip_all else R.string.action_skip)) }
                    OutlinedButton(
                        onClick = onClose,
                        shape = MaterialTheme.shapes.large,
                        contentPadding = buttonPadding,
                        modifier = buttonModifier.weight(1f),
                    ) { ButtonLabel(Icons.Rounded.Close, stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

/** An answer button's icon and label, with an optional smaller second line. */
@Composable
private fun ButtonLabel(icon: ImageVector, text: String, detail: String? = null) {
    Icon(icon, contentDescription = null)
    Column(modifier = Modifier.padding(start = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AnswerButtons(onSkip: () -> Unit, onTake: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        OutlinedButton(onClick = onSkip) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null)
            Text(stringResource(R.string.action_skip), modifier = Modifier.padding(start = 6.dp))
        }
        Button(onClick = onTake) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(stringResource(R.string.action_take), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
