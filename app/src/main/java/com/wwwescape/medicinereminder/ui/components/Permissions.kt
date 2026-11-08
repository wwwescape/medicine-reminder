package com.wwwescape.medicinereminder.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.reminders.ReminderNotifier
import com.wwwescape.medicinereminder.data.reminders.Reminders
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** The current time, refreshed every 30 seconds so "due" turns into "missed" on its own. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { now = LocalDateTime.now() }
    return now
}

/** What reminders need from the system, re-checked whenever the app comes back to the front. */
data class ReminderAccess(val notifications: Boolean, val exactAlarms: Boolean, val fullScreen: Boolean) {
    val complete get() = notifications && exactAlarms && fullScreen
}

fun reminderAccess(context: Context) = ReminderAccess(
    notifications = ReminderNotifier.canNotify(context),
    exactAlarms = Reminders.canScheduleExact(context),
    fullScreen = ReminderNotifier.canUseFullScreen(context),
)

@Composable
fun rememberReminderAccess(): ReminderAccess {
    val context = LocalContext.current
    var access by remember { mutableStateOf(reminderAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = reminderAccess(context) }
    return access
}

fun exactAlarmSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        appDetailsIntent(context)
    }

fun fullScreenSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
    } else {
        appDetailsIntent(context)
    }

fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun appDetailsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

/** Cards asking for each missing access, most important first. Shows nothing once all are granted. */
@Composable
fun PermissionPrompts(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val access = rememberReminderAccess()
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) context.startActivity(notificationSettingsIntent(context))
    }
    if (access.complete) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!access.notifications) {
            PermissionCard(
                icon = Icons.Rounded.NotificationsActive,
                title = stringResource(R.string.permission_notifications_title),
                body = stringResource(R.string.permission_notifications_body),
                action = stringResource(R.string.action_allow),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(notificationSettingsIntent(context))
                    }
                },
            )
        }
        if (!access.exactAlarms) {
            PermissionCard(
                icon = Icons.Rounded.Alarm,
                title = stringResource(R.string.permission_exact_title),
                body = stringResource(R.string.permission_exact_body),
                action = stringResource(R.string.action_allow),
                onClick = { context.startActivity(exactAlarmSettingsIntent(context)) },
            )
        }
        if (!access.fullScreen) {
            PermissionCard(
                icon = Icons.Rounded.Fullscreen,
                title = stringResource(R.string.permission_full_screen_title),
                body = stringResource(R.string.permission_full_screen_body),
                action = stringResource(R.string.action_allow),
                onClick = { context.startActivity(fullScreenSettingsIntent(context)) },
            )
        }
    }
}
