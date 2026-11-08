package com.wwwescape.medicinereminder.ui.screens.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.medicinereminder.data.backup.Backup
import com.wwwescape.medicinereminder.data.backup.BackupData
import com.wwwescape.medicinereminder.data.backup.BackupException
import com.wwwescape.medicinereminder.data.backup.BackupPreview
import com.wwwescape.medicinereminder.data.settings.AppSettings
import com.wwwescape.medicinereminder.data.settings.ColorTheme
import com.wwwescape.medicinereminder.data.settings.FollowUp
import com.wwwescape.medicinereminder.data.settings.SettingsRepository
import com.wwwescape.medicinereminder.data.settings.SnoozeLength
import com.wwwescape.medicinereminder.data.settings.ThemeContrast
import com.wwwescape.medicinereminder.data.settings.ThemeMode
import com.wwwescape.medicinereminder.data.settings.WeekStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where an export or import stands, for the Backup dialogs. */
sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    /** A checked backup waiting for the user to confirm it should replace everything. */
    data class Confirm(val preview: BackupPreview) : BackupState
    data class Exported(val data: BackupData) : BackupState
    data class Restored(val data: BackupData) : BackupState
    data class Failed(val reason: BackupException.Reason) : BackupState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    var backupState by mutableStateOf<BackupState>(BackupState.Idle)
        private set

    fun exportTo(target: Uri) {
        backupState = BackupState.Working
        viewModelScope.launch {
            backupState = try {
                val app = getApplication<Application>()
                val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
                BackupState.Exported(Backup.export(app, target, version))
            } catch (e: Exception) {
                BackupState.Failed(BackupException.Reason.UNREADABLE)
            }
        }
    }

    fun importFrom(source: Uri) {
        backupState = BackupState.Working
        viewModelScope.launch {
            backupState = try {
                BackupState.Confirm(Backup.read(getApplication(), source))
            } catch (e: BackupException) {
                BackupState.Failed(e.reason)
            }
        }
    }

    fun confirmRestore() {
        val preview = (backupState as? BackupState.Confirm)?.preview ?: return
        backupState = BackupState.Working
        viewModelScope.launch {
            backupState = try {
                Backup.restore(getApplication(), preview)
                BackupState.Restored(preview.data)
            } catch (e: Exception) {
                Backup.discard(preview)
                BackupState.Failed(BackupException.Reason.UNREADABLE)
            }
        }
    }

    fun dismissBackup() {
        (backupState as? BackupState.Confirm)?.let { Backup.discard(it.preview) }
        backupState = BackupState.Idle
    }

    val settings: StateFlow<AppSettings> = SettingsRepository.settingsFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private fun save(block: suspend (Context) -> Unit) {
        viewModelScope.launch { block(getApplication()) }
    }

    fun setThemeMode(mode: ThemeMode) = save { SettingsRepository.setThemeMode(it, mode) }
    fun setDynamicColor(enabled: Boolean) = save { SettingsRepository.setDynamicColor(it, enabled) }
    fun setColorTheme(theme: ColorTheme) = save { SettingsRepository.setColorTheme(it, theme) }
    fun setThemeContrast(contrast: ThemeContrast) = save { SettingsRepository.setThemeContrast(it, contrast) }
    fun setPureDark(enabled: Boolean) = save { SettingsRepository.setPureDark(it, enabled) }
    fun setAbsoluteDark(enabled: Boolean) = save { SettingsRepository.setAbsoluteDark(it, enabled) }
    fun setSnooze(snooze: SnoozeLength) = save { SettingsRepository.setSnooze(it, snooze) }
    fun setFollowUp(followUp: FollowUp) = save { SettingsRepository.setFollowUp(it, followUp) }
    fun setFullScreen(enabled: Boolean) = save { SettingsRepository.setFullScreen(it, enabled) }
    fun setWeekStart(weekStart: WeekStart) = save { SettingsRepository.setWeekStart(it, weekStart) }
}
