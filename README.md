<p align="center">
  <img src="assets/logo.svg" alt="Medicine Reminder logo" width="120" />
</p>

<h1 align="center">Medicine Reminder</h1>

<p align="center">
  A medicine reminder for Android that rings like an alarm, full screen, until each dose is
  answered. Organised by course of treatment, with customisable 3D pill icons and a clear record
  of what was taken, skipped or missed. No account, no analytics, no internet access.
</p>

## Features

- **Courses first** — everything starts with a course: one prescription or treatment, with a
  start date and a fixed number of days (or ongoing), an optional doctor or clinic, notes, and any
  number of prescription photos or PDFs, copied into the app.
- **Medicines in each course** — dose and unit (tablets, drops, puffs, ml, units…), strength,
  instructions with quick picks ("Both eyes", "After food"…), and a schedule: every day, every few
  days, specific weekdays, or as needed, at any number of times. A medicine can follow its course's
  dates or have its own.
- **Your medicine, as it looks** — 25 shapes (round, scored, two-color, oval, caplet, capsule,
  softgel, triangle, square, diamond, hexagon, heart, drops, syrup, nasal spray, inhaler, syringe,
  injection pen, vial, ampoule, cream tube, patch, sachet, suppository) in 26 colors, two-tone where
  the shape has two parts. Drawn in code, so they're sharp at any size.
- **Full-screen reminders** — an exact alarm rings when a dose is due, over the lock screen, with
  Take / Skip per medicine, Take all, and Snooze. Unanswered reminders ring again (every 5–30 min,
  up to 3 times). Notification actions work without opening the app.
- **Missed days, filled in** — the Today screen steps through any day; past doses can be answered
  afterwards. Each course shows taken / missed / skipped, how far through it you are, and a
  day-by-day history grid.
- **On your watch too** — with the Wear OS companion installed, a due dose also rings full screen
  on the watch, with a prominent buzz (and no sound). Take, Skip, Snooze or Close there or on the phone,
  and both stop. The phone stays in charge: the watch keeps no data of its own.
- **Material 3 Expressive design** — light, dark, and system themes, dynamic color (Material You),
  16+ curated color themes, adjustable contrast, and Pure Black / Absolute Black modes.

## Development

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Narwhal or newer)
- JDK 17+ (bundled with Android Studio)
- An Android device or emulator running Android 8.0 (API 26) or newer

### Build & run

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Release a new version

Bump `appVersionCode` and `appVersionName` in `gradle.properties` (shared by the phone and watch
apps; the watch build adds 100000 to the code), commit, then:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The tag push builds signed release APKs and AABs of the phone app and the Wear OS companion
(`medicine-reminder-phone-<tag>.apk/.aab`, `medicine-reminder-watch-<tag>.apk/.aab`) and attaches
them to a new GitHub Release (see
`.github/workflows/release.yml`). It needs the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, and `KEY_PASSWORD` repository secrets, which are kept locally in the gitignored
`keystore.properties`.

### Icons

The launcher icon is Material Symbols Outlined "prescriptions" in #2A7FFF on white, which is also
the status-bar icon. Regenerate every icon resource with `python scripts/generate_icons.py`
(needs `pip install resvg-py`).

### Project layout

- `data/db` — Room entities (courses, prescription files, medicines, dose log) and DAOs
- `data/schedule` — the pure scheduling rules (which doses fall on a day, the next reminder,
  adherence), covered by unit tests
- `data/reminders` — exact alarms, the ringing notification and its receivers
- `ui/icons` — the medicine shape renderer, shared by Compose and notification icons
- `ui/screens` — Today, Courses, course detail and editor, medicine editor, settings
- `ui/reminder` — the full-screen reminder activity
- `data/wear` — tells the Wear OS companion when a reminder rings or stops, and takes its answers
- `wear/` — the Wear OS companion: shows the phone's reminders full screen and sends answers back

Room schema history is kept in `app/schemas`.
