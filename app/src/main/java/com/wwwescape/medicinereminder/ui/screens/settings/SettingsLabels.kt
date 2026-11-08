package com.wwwescape.medicinereminder.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.wwwescape.medicinereminder.R
import com.wwwescape.medicinereminder.data.settings.ColorTheme
import com.wwwescape.medicinereminder.data.settings.FollowUp
import com.wwwescape.medicinereminder.data.settings.SnoozeLength
import com.wwwescape.medicinereminder.data.settings.ThemeContrast
import com.wwwescape.medicinereminder.data.settings.ThemeMode
import com.wwwescape.medicinereminder.data.settings.WeekStart
import java.time.format.TextStyle

@Composable
fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
        ThemeMode.SYSTEM -> R.string.theme_mode_system
    },
)

@Composable
fun ColorTheme.label(): String = stringResource(
    when (this) {
        ColorTheme.DEFAULT -> R.string.color_theme_default
        ColorTheme.OCEAN -> R.string.color_theme_ocean
        ColorTheme.FOREST -> R.string.color_theme_forest
        ColorTheme.SUNSET -> R.string.color_theme_sunset
        ColorTheme.GRAPE -> R.string.color_theme_grape
        ColorTheme.ROSE -> R.string.color_theme_rose
        ColorTheme.SLATE -> R.string.color_theme_slate
        ColorTheme.GOLD -> R.string.color_theme_gold
        ColorTheme.TEAL -> R.string.color_theme_teal
        ColorTheme.PLUM -> R.string.color_theme_plum
        ColorTheme.MOSS -> R.string.color_theme_moss
        ColorTheme.CORAL -> R.string.color_theme_coral
        ColorTheme.INDIGO -> R.string.color_theme_indigo
        ColorTheme.MUSTARD -> R.string.color_theme_mustard
        ColorTheme.CRIMSON -> R.string.color_theme_crimson
        ColorTheme.MINT -> R.string.color_theme_mint
        ColorTheme.PERIWINKLE -> R.string.color_theme_periwinkle
    },
)

@Composable
fun ThemeContrast.label(): String = stringResource(
    when (this) {
        ThemeContrast.STANDARD -> R.string.contrast_standard
        ThemeContrast.MEDIUM -> R.string.contrast_medium
        ThemeContrast.HIGH -> R.string.contrast_high
    },
)

@Composable
fun SnoozeLength.label(): String = pluralStringResource(R.plurals.minutes, minutes, minutes)

@Composable
fun FollowUp.label(): String = minutes?.let { pluralStringResource(R.plurals.follow_up_every, it, it) }
    ?: stringResource(R.string.follow_up_off)

/** "Sunday" or "Monday", in the phone's language. */
@Composable
fun WeekStart.label(): String = day.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
