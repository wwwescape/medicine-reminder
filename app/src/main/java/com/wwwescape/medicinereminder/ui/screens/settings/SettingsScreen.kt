package com.wwwescape.medicinereminder.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.reminders.ReminderNotifier
import com.wwwescape.medicinereminder.data.reminders.Reminders
import com.wwwescape.medicinereminder.data.settings.FollowUp
import com.wwwescape.medicinereminder.data.settings.SnoozeLength
import com.wwwescape.medicinereminder.data.settings.ThemeContrast
import com.wwwescape.medicinereminder.data.settings.ThemeMode
import com.wwwescape.medicinereminder.data.settings.WeekStart
import com.wwwescape.medicinereminder.ui.components.NavigationRow
import com.wwwescape.medicinereminder.ui.components.ReadableWidth
import com.wwwescape.medicinereminder.ui.components.RowDivider
import com.wwwescape.medicinereminder.ui.components.SectionLabel
import com.wwwescape.medicinereminder.ui.components.SettingsGroupCard
import com.wwwescape.medicinereminder.ui.components.ToggleRow
import com.wwwescape.medicinereminder.ui.components.exactAlarmSettingsIntent
import com.wwwescape.medicinereminder.ui.components.fullScreenSettingsIntent
import com.wwwescape.medicinereminder.ui.components.notificationSettingsIntent
import com.wwwescape.medicinereminder.ui.components.rememberReminderAccess
import com.wwwescape.medicinereminder.ui.reminder.ReminderActivity
import com.wwwescape.medicinereminder.util.openUrl

private const val PRIVACY_POLICY_URL = "https://www.ericppereira.co.in/apps/medicine-reminder/privacy-policy.html"

private enum class SettingsDialog { THEME, COLOR_THEME, CONTRAST, SNOOZE, FOLLOW_UP, TEST_REMINDER, WEEK_START }

/** How long "In 10 seconds" waits: enough to lock the phone and see the reminder as it really appears. */
private const val TEST_DELAY_SECONDS = 10

@Composable
fun SettingsScreen(
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }
    val isDarkTheme = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    val access = rememberReminderAccess()
    val reminders = settings.reminders

    ReadableWidth(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(stringResource(R.string.section_reminders))
            SettingsGroupCard {
                NavigationRow(
                    icon = Icons.Rounded.Snooze,
                    title = stringResource(R.string.setting_snooze),
                    subtitle = reminders.snooze.label(),
                    onClick = { dialog = SettingsDialog.SNOOZE },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.Replay,
                    title = stringResource(R.string.setting_follow_up),
                    subtitle = reminders.followUp.label(),
                    onClick = { dialog = SettingsDialog.FOLLOW_UP },
                )
                RowDivider()
                ToggleRow(
                    icon = Icons.Rounded.Fullscreen,
                    title = stringResource(R.string.setting_full_screen),
                    subtitle = stringResource(R.string.setting_full_screen_subtitle),
                    checked = reminders.fullScreen,
                    onCheckedChange = viewModel::setFullScreen,
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.setting_test_reminder),
                    subtitle = stringResource(R.string.setting_test_reminder_subtitle),
                    onClick = { dialog = SettingsDialog.TEST_REMINDER },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.MusicNote,
                    title = stringResource(R.string.setting_sound),
                    subtitle = stringResource(R.string.setting_sound_subtitle),
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = {
                        ReminderNotifier.ensureChannel(context)
                        context.startActivity(notificationSettingsIntent(context))
                    },
                )
                RowDivider()
                SoundCredit()
            }

            SectionLabel(stringResource(R.string.section_access))
            SettingsGroupCard {
                NavigationRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.setting_access_notifications),
                    subtitle = stringResource(if (access.notifications) R.string.access_granted else R.string.access_required),
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { context.startActivity(notificationSettingsIntent(context)) },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.Alarm,
                    title = stringResource(R.string.setting_access_exact),
                    subtitle = stringResource(if (access.exactAlarms) R.string.access_granted else R.string.access_required),
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { context.startActivity(exactAlarmSettingsIntent(context)) },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.Fullscreen,
                    title = stringResource(R.string.setting_access_full_screen),
                    subtitle = stringResource(if (access.fullScreen) R.string.access_granted else R.string.access_required),
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { context.startActivity(fullScreenSettingsIntent(context)) },
                )
            }

            SectionLabel(stringResource(R.string.section_appearance))
            SettingsGroupCard {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.setting_dynamic_color),
                        subtitle = stringResource(R.string.setting_dynamic_color_subtitle),
                        checked = settings.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                    RowDivider()
                }
                NavigationRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.setting_color_theme),
                    subtitle = settings.colorTheme.label(),
                    onClick = { dialog = SettingsDialog.COLOR_THEME },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.DarkMode,
                    title = stringResource(R.string.setting_theme),
                    subtitle = settings.themeMode.label(),
                    onClick = { dialog = SettingsDialog.THEME },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.CalendarMonth,
                    title = stringResource(R.string.setting_week_start),
                    subtitle = settings.weekStart.label(),
                    onClick = { dialog = SettingsDialog.WEEK_START },
                )
                // Contrast/pure dark/absolute dark only affect dark theme — hidden entirely when the
                // effective theme (accounting for "System default" resolving to light) is light.
                if (isDarkTheme) {
                    RowDivider()
                    NavigationRow(
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.setting_theme_contrast),
                        subtitle = settings.themeContrast.label(),
                        onClick = { dialog = SettingsDialog.CONTRAST },
                    )
                    RowDivider()
                    ToggleRow(
                        icon = Icons.Rounded.Brightness6,
                        title = stringResource(R.string.setting_pure_dark),
                        subtitle = stringResource(R.string.setting_pure_dark_subtitle),
                        checked = settings.pureDark,
                        onCheckedChange = viewModel::setPureDark,
                    )
                    RowDivider()
                    ToggleRow(
                        icon = Icons.Rounded.NightsStay,
                        title = stringResource(R.string.setting_absolute_dark),
                        subtitle = stringResource(R.string.setting_absolute_dark_subtitle),
                        checked = settings.absoluteDark,
                        onCheckedChange = viewModel::setAbsoluteDark,
                    )
                }
            }

            BackupSection(viewModel)

            SectionLabel(stringResource(R.string.section_about))
            SettingsGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.about_version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RowDivider()
                Text(
                    text = stringResource(R.string.privacy_statement_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.section_privacy_policy),
                    subtitle = null,
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { openUrl(context, PRIVACY_POLICY_URL) },
                )
                RowDivider()
                NavigationRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.section_open_source_licenses),
                    subtitle = stringResource(R.string.settings_row_licenses_subtitle),
                    onClick = onNavigateToLicenses,
                )
            }
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        SettingsDialog.THEME -> SettingsPickerDialog(
            title = stringResource(R.string.setting_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label() },
            onSelect = viewModel::setThemeMode,
            onDismiss = dismiss,
        )
        SettingsDialog.COLOR_THEME -> ThemePickerDialog(
            selected = settings.colorTheme,
            onSelect = viewModel::setColorTheme,
            onDismiss = dismiss,
        )
        SettingsDialog.CONTRAST -> SettingsPickerDialog(
            title = stringResource(R.string.setting_theme_contrast),
            options = ThemeContrast.entries,
            selected = settings.themeContrast,
            label = { it.label() },
            onSelect = viewModel::setThemeContrast,
            onDismiss = dismiss,
        )
        SettingsDialog.SNOOZE -> SettingsPickerDialog(
            title = stringResource(R.string.setting_snooze),
            options = SnoozeLength.entries,
            selected = reminders.snooze,
            label = { it.label() },
            onSelect = viewModel::setSnooze,
            onDismiss = dismiss,
        )
        SettingsDialog.TEST_REMINDER -> {
            val scheduledText = stringResource(R.string.toast_test_scheduled, TEST_DELAY_SECONDS)
            AlertDialog(
                onDismissRequest = dismiss,
                icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                title = { Text(stringResource(R.string.setting_test_reminder)) },
                text = { Text(stringResource(R.string.test_reminder_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        dismiss()
                        if (!ReminderNotifier.canNotify(context)) {
                            Toast.makeText(context, R.string.toast_allow_notifications, Toast.LENGTH_LONG).show()
                        } else {
                            Reminders.scheduleTest(context, TEST_DELAY_SECONDS)
                            Toast.makeText(context, scheduledText, Toast.LENGTH_LONG).show()
                        }
                    }) { Text(stringResource(R.string.action_test_later, TEST_DELAY_SECONDS)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        dismiss()
                        if (!ReminderNotifier.canNotify(context)) {
                            Toast.makeText(context, R.string.toast_allow_notifications, Toast.LENGTH_LONG).show()
                        } else {
                            ReminderNotifier.showTest(context, reminders.fullScreen)
                            context.startActivity(ReminderActivity.testIntent(context))
                        }
                    }) { Text(stringResource(R.string.action_test_now)) }
                },
            )
        }
        SettingsDialog.WEEK_START -> SettingsPickerDialog(
            title = stringResource(R.string.setting_week_start),
            options = WeekStart.entries,
            selected = settings.weekStart,
            label = { it.label() },
            onSelect = viewModel::setWeekStart,
            onDismiss = dismiss,
        )
        SettingsDialog.FOLLOW_UP -> SettingsPickerDialog(
            title = stringResource(R.string.setting_follow_up),
            options = FollowUp.entries,
            selected = reminders.followUp,
            label = { it.label() },
            onSelect = viewModel::setFollowUp,
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

/** Attribution for the bundled reminder sound, as Pixabay words it: the author and Pixabay
 * are each a link. Sits under the sound it credits, like the music credit in Photo Slideshow. */
@Composable
private fun SoundCredit() {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val sentence = stringResource(R.string.sound_credit, ReminderNotifier.SOUND_CREDIT_AUTHOR, ReminderNotifier.SOUND_CREDIT_SOURCE)
    val text = buildAnnotatedString {
        append(sentence)
        listOf(
            ReminderNotifier.SOUND_CREDIT_AUTHOR to ReminderNotifier.SOUND_CREDIT_AUTHOR_URL,
            ReminderNotifier.SOUND_CREDIT_SOURCE to ReminderNotifier.SOUND_CREDIT_SOURCE_URL,
        ).forEach { (name, url) ->
            val at = sentence.indexOf(name)
            if (at >= 0) addLink(LinkAnnotation.Url(url, linkStyle), at, at + name.length)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    )
}
