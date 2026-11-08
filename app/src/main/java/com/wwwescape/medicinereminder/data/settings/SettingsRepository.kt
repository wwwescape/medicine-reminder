package com.wwwescape.medicinereminder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Mirrors [ThemeMode] outside DataStore, which is Flow-only/async. [MainActivity] reads this
 * synchronously in `attachBaseContext`, before any Compose or DataStore code can run, so the
 * window's initial (pre-Compose) theme resolution honors the user's choice instead of always
 * following the raw system day/night setting. */
const val THEME_PREFS_NAME = "theme_prefs_sync"
const val THEME_MODE_PREF_KEY = "theme_mode"

object SettingsRepository {

    private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    private val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
    private val THEME_CONTRAST_KEY = stringPreferencesKey("theme_contrast")
    private val PURE_DARK_KEY = booleanPreferencesKey("pure_dark")
    private val ABSOLUTE_DARK_KEY = booleanPreferencesKey("absolute_dark")
    private val SNOOZE_KEY = stringPreferencesKey("snooze")
    private val FOLLOW_UP_KEY = stringPreferencesKey("follow_up")
    private val FULL_SCREEN_KEY = booleanPreferencesKey("full_screen")
    private val SAVE_TO_DOCUMENTS_KEY = booleanPreferencesKey("save_to_documents")
    private val WEEK_START_KEY = stringPreferencesKey("week_start")

    fun settingsFlow(context: Context): Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME_MODE_KEY].toEnumOrDefault(ThemeMode.SYSTEM),
            useDynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: true,
            colorTheme = prefs[COLOR_THEME_KEY].toEnumOrDefault(ColorTheme.DEFAULT),
            themeContrast = prefs[THEME_CONTRAST_KEY].toEnumOrDefault(ThemeContrast.STANDARD),
            pureDark = prefs[PURE_DARK_KEY] ?: false,
            absoluteDark = prefs[ABSOLUTE_DARK_KEY] ?: false,
            reminders = ReminderSettings(
                snooze = prefs[SNOOZE_KEY].toEnumOrDefault(SnoozeLength.TEN),
                followUp = prefs[FOLLOW_UP_KEY].toEnumOrDefault(FollowUp.TEN),
                fullScreen = prefs[FULL_SCREEN_KEY] ?: true,
            ),
            saveToDocuments = prefs[SAVE_TO_DOCUMENTS_KEY] ?: false,
            weekStart = prefs[WEEK_START_KEY].toEnumOrDefault(WeekStart.forLocale()),
        )
    }

    suspend fun current(context: Context): AppSettings = settingsFlow(context).first()

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME_MODE_KEY] = mode.name }
        context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(THEME_MODE_PREF_KEY, mode.name).apply()
    }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) = put(context, DYNAMIC_COLOR_KEY, enabled)
    suspend fun setColorTheme(context: Context, theme: ColorTheme) = put(context, COLOR_THEME_KEY, theme.name)
    suspend fun setThemeContrast(context: Context, contrast: ThemeContrast) = put(context, THEME_CONTRAST_KEY, contrast.name)
    suspend fun setPureDark(context: Context, enabled: Boolean) = put(context, PURE_DARK_KEY, enabled)
    suspend fun setAbsoluteDark(context: Context, enabled: Boolean) = put(context, ABSOLUTE_DARK_KEY, enabled)
    suspend fun setSnooze(context: Context, snooze: SnoozeLength) = put(context, SNOOZE_KEY, snooze.name)
    suspend fun setFollowUp(context: Context, followUp: FollowUp) = put(context, FOLLOW_UP_KEY, followUp.name)
    suspend fun setFullScreen(context: Context, enabled: Boolean) = put(context, FULL_SCREEN_KEY, enabled)
    suspend fun setSaveToDocuments(context: Context, enabled: Boolean) = put(context, SAVE_TO_DOCUMENTS_KEY, enabled)
    suspend fun setWeekStart(context: Context, weekStart: WeekStart) = put(context, WEEK_START_KEY, weekStart.name)

    /** Every stored setting by key, for a backup. */
    suspend fun allPreferences(context: Context): Map<String, Any> =
        context.settingsDataStore.data.first().asMap().mapKeys { it.key.name }

    /** Replaces every setting with [values] from a backup, keeping each value's own type. */
    suspend fun restorePreferences(context: Context, values: Map<String, Any>) {
        context.settingsDataStore.edit { prefs ->
            prefs.clear()
            values.forEach { (name, value) ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is Int -> prefs[intPreferencesKey(name)] = value
                    is Long -> prefs[longPreferencesKey(name)] = value
                    is Float -> prefs[floatPreferencesKey(name)] = value
                    is String -> prefs[stringPreferencesKey(name)] = value
                    is Set<*> -> prefs[stringSetPreferencesKey(name)] = value as Set<String>
                }
            }
        }
        // Keep the synchronous theme mirror that MainActivity reads before DataStore is ready.
        setThemeMode(context, settingsFlow(context).first().themeMode)
    }

    private suspend fun <T> put(context: Context, key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { name -> runCatching { enumValueOf<T>(name) }.getOrNull() } ?: default
}
