package com.wwwescape.medicinereminder.data.settings

import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** [seedHue] is a 0-359 HSV hue driving [com.wwwescape.medicinereminder.ui.theme.generateColorScheme].
 * DEFAULT (null) keeps the app's own hand-authored static palette (see Color.kt) instead of a
 * generated one. Ignored entirely when dynamic color is on, which takes precedence. */
enum class ColorTheme(val seedHue: Float?) {
    DEFAULT(null),
    OCEAN(205f),
    FOREST(140f),
    SUNSET(25f),
    GRAPE(280f),
    ROSE(340f),
    SLATE(220f),
    GOLD(45f),
    TEAL(175f),
    PLUM(300f),
    MOSS(95f),
    CORAL(12f),
    INDIGO(245f),
    MUSTARD(55f),
    CRIMSON(355f),
    MINT(160f),
    PERIWINKLE(230f),
}

/** Only affects (or only makes sense for) dark theme. */
enum class ThemeContrast { STANDARD, MEDIUM, HIGH }

/** How long "Snooze" waits before reminding again. */
enum class SnoozeLength(val minutes: Int) { FIVE(5), TEN(10), FIFTEEN(15), THIRTY(30), SIXTY(60) }

/** If a reminder goes unanswered, remind again after this long, up to [MAX_FOLLOW_UPS] times.
 * [minutes] is null for OFF. */
enum class FollowUp(val minutes: Int?) { OFF(null), FIVE(5), TEN(10), FIFTEEN(15), THIRTY(30) }

const val MAX_FOLLOW_UPS = 3

data class ReminderSettings(
    val snooze: SnoozeLength = SnoozeLength.TEN,
    val followUp: FollowUp = FollowUp.TEN,
    /** Open the full-screen reminder (over the lock screen) rather than only a notification. */
    val fullScreen: Boolean = true,
)

/** The first day of the week on the Today screen's date row and in weekday pickers. */
enum class WeekStart(val day: DayOfWeek) {
    SUNDAY(DayOfWeek.SUNDAY),
    MONDAY(DayOfWeek.MONDAY);

    companion object {
        /** What the phone's region uses, until the user picks: Sunday in the US, Monday in most
         * of Europe, and so on. */
        fun forLocale(locale: Locale = Locale.getDefault()): WeekStart =
            if (WeekFields.of(locale).firstDayOfWeek == DayOfWeek.SUNDAY) SUNDAY else MONDAY
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val colorTheme: ColorTheme = ColorTheme.DEFAULT,
    val themeContrast: ThemeContrast = ThemeContrast.STANDARD,
    val pureDark: Boolean = false,
    val absoluteDark: Boolean = false,
    val reminders: ReminderSettings = ReminderSettings(),
    /** Also keep a copy of every prescription file added to a course in Documents. Off until
     * the user turns it on; single files can always be saved by hand. */
    val saveToDocuments: Boolean = false,
    val weekStart: WeekStart = WeekStart.forLocale(),
)
