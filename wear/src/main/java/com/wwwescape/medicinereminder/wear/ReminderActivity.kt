package com.wwwescape.medicinereminder.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch

/**
 * The reminder on the watch: on screen, over the watch face, with a prominent buzz (no sound)
 * until one of Take, Snooze, Skip or Close is tapped here, or the reminder is answered on the
 * phone. The answer goes to the phone, which logs it.
 */
class ReminderActivity : ComponentActivity() {

    private var reminder by mutableStateOf<WatchReminder?>(null)
    private lateinit var buzzer: Buzzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buzzer = Buzzer(applicationContext)
        load()

        lifecycleScope.launch {
            WatchReminders.dismissed.collect { slot -> if (slot == reminder?.slot) close() }
        }
        lifecycleScope.launch {
            WatchReminders.shown.collect { load() }
        }

        setContent {
            MaterialTheme {
                reminder?.let { ReminderScreen(it, ::answer) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load()
    }

    override fun onDestroy() {
        buzzer.stop()
        super.onDestroy()
    }

    /** Shows the reminder the phone last sent, or closes if it has stopped ringing meanwhile. */
    private fun load() {
        val current = WatchReminders.current(this)
        if (current == null) {
            close()
            return
        }
        reminder = current
        buzzer.start()
    }

    private fun answer(action: String) {
        reminder?.let { WatchReminders.answer(this, it, action) }
        close()
    }

    private fun close() {
        buzzer.stop()
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

@Composable
private fun ReminderScreen(reminder: WatchReminder, onAnswer: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    AppScaffold {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = reminder.time,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (reminder.test) {
                            Text(
                                text = stringResource(R.string.test_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                reminder.doses.forEach { dose ->
                    item { DoseRow(dose) }
                }
                item {
                    AnswerButton(
                        label = stringResource(if (reminder.doses.size > 1) R.string.action_take_all else R.string.action_take),
                        icon = Icons.Rounded.Check,
                        style = AnswerStyle.Primary,
                        onClick = { onAnswer(WearProtocol.ACTION_TAKE) },
                    )
                }
                // The same order as on the phone: Take, Snooze, Skip, Close.
                item {
                    AnswerButton(stringResource(R.string.action_snooze), Icons.Rounded.Snooze, AnswerStyle.Tonal) {
                        onAnswer(WearProtocol.ACTION_SNOOZE)
                    }
                }
                item {
                    AnswerButton(stringResource(R.string.action_skip), Icons.Rounded.SkipNext, AnswerStyle.Tonal) {
                        onAnswer(WearProtocol.ACTION_SKIP)
                    }
                }
                item {
                    AnswerButton(stringResource(R.string.action_close), Icons.Rounded.Close, AnswerStyle.Outlined) {
                        onAnswer(WearProtocol.ACTION_CLOSE)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseRow(dose: WatchDose) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dose.icon?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp)) }
        Column(Modifier.weight(1f)) {
            Text(dose.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(dose.dose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (dose.instructions.isNotBlank()) {
                Text(
                    dose.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

private enum class AnswerStyle { Primary, Tonal, Outlined }

@Composable
private fun AnswerButton(label: String, icon: ImageVector, style: AnswerStyle, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
    val iconContent: @Composable BoxScope.() -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
    val labelContent: @Composable RowScope.() -> Unit = { Text(label) }
    when (style) {
        AnswerStyle.Primary -> Button(onClick = onClick, modifier = modifier, icon = iconContent, label = labelContent)
        AnswerStyle.Tonal -> FilledTonalButton(onClick = onClick, modifier = modifier, icon = iconContent, label = labelContent)
        AnswerStyle.Outlined -> OutlinedButton(onClick = onClick, modifier = modifier, icon = iconContent, label = labelContent)
    }
}
